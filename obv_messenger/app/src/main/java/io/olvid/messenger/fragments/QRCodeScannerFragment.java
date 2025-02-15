/*
 *  Olvid for Android
 *  Copyright © 2019-2022 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.messenger.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.olvid.engine.Logger;
import io.olvid.engine.datatypes.NoExceptionSingleThreadExecutor;
import io.olvid.messenger.App;
import io.olvid.messenger.R;

public class QRCodeScannerFragment extends Fragment {
    private static final int PERMISSION_CAMERA = 658;
    private Context context;
    private PreviewView previewView;
    private NoExceptionSingleThreadExecutor executor;
    private MultiFormatReader reader;
    private ResultHandler resultHandler;
    private CameraControl cameraControl;
    private boolean useFrontCamera = false;

    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    startPreviewAndQRCodeDetection();
                } else {
                    App.toast(R.string.toast_message_camera_permission_denied, Toast.LENGTH_SHORT);
                }
            });

    public void setResultHandler(ResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void switchCamera() {
        useFrontCamera = !useFrontCamera;
        requestAndStartCamera();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        this.context = context;
        this.executor = new NoExceptionSingleThreadExecutor("QRCodeScannerFragment-ImageAnalysis");
        this.reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        this.reader.setHints(hints);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_qr_code_scanner, container, false);
        previewView = rootView.findViewById(R.id.qr_code_scanner_preview_view);

        requestAndStartCamera();
        return rootView;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        previewView.setOnTouchListener((v, event) -> {
            if (cameraControl != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(v.getWidth(), v.getHeight());
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .addPoint(point, FocusMeteringAction.FLAG_AE)
                        .build();
                cameraControl.startFocusAndMetering(action);
                return true;
            }
            return false;
        });
    }

    private void requestAndStartCamera() {
        boolean hasCamera = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (hasCamera) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission.launch(Manifest.permission.CAMERA);
            } else {
                startPreviewAndQRCodeDetection();
            }
        }
    }


    private void startPreviewAndQRCodeDetection() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(useFrontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                        .build();


                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
                    boolean stop = false;

                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        if (stop) {
                            image.close();
                            return;
                        }
                        // image is YUV --> first plane is luminance
                        ByteBuffer data = image.getPlanes()[0].getBuffer();
                        data.rewind();
                        byte[] bytes = new byte[data.remaining()];
                        data.get(bytes);
                        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                                bytes,
                                image.getWidth(),
                                image.getHeight(),
                                0,
                                0,
                                image.getWidth(),
                                image.getHeight(),
                                false);
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                        try {
                            Result result = reader.decode(bitmap);
                            if (resultHandler != null) {
                                if (resultHandler.handleResult(result)) {
                                    stop = true;
                                } else {
                                    Thread.sleep(1000);
                                }
                            }
                        } catch (NotFoundException | InterruptedException e) {
                            // nothing to do
                        }
                        image.close();
                    }
                });

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
                cameraControl = camera.getCameraControl();
            } catch (ExecutionException | InterruptedException | IllegalArgumentException e) {
                Logger.e("Unexpected exception in cemaraX preview.");
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public interface ResultHandler {
        boolean handleResult(Result result);
    }
}
