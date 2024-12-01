import 'dart:typed_data';
import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'controller/opencv_helper.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  CameraController? controller;
  Future<void>? initializeControllerFuture;
  bool isProcessing = false;
  Uint8List? grayImageBytes;
  OpenCVHelper opencvHelper = OpenCVHelper();

  @override
  void initState() {
    super.initState();
    initializeCamera();
  }

  Future<void> initializeCamera() async {
    final cameras = await availableCameras();
    final firstCamera = cameras.first;

    controller = CameraController(
      firstCamera,
      ResolutionPreset.high,
    );

    initializeControllerFuture = controller!.initialize();
    setState(() {});
  }

  @override
  void dispose() {
    controller?.dispose();
    super.dispose();
  }

  void processImage(CameraImage image) async {
    if (isProcessing) return;
    isProcessing = true;
    try {
      final frame = opencvHelper.convertYUVToRGB(image);
      final (gray) = await opencvHelper.convertRGBToGray(frame);
      final (orb) = await opencvHelper.extractOrbFeatures(gray, frame);
      grayImageBytes = opencvHelper.convertMatToImage(orb);

      setState(() {});
    } catch (e) {
      print("Error processing image: $e");
    } finally {
      isProcessing = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: FutureBuilder<void>(
          future: initializeControllerFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.done) {
              return LayoutBuilder(
                builder: (context, constraints) {
                  return Stack(
                    children: [
                      SizedBox(
                        width: constraints.maxWidth,
                        height: constraints.maxHeight,
                        child: CameraPreview(controller!),
                      ),
                      if (grayImageBytes != null)
                        Align(
                          alignment: Alignment.bottomCenter,
                          child: SizedBox(
                            width: constraints.maxWidth,
                            height: constraints.maxHeight * 0.5,
                            child: SizedBox(
                              width: constraints.maxWidth,
                              height: constraints.maxHeight * 0.5,
                              child: Image.memory(grayImageBytes!),
                            ),
                          ),
                        ),
                    ],
                  );
                },
              );
            } else {
              return const Center(child: CircularProgressIndicator());
            }
          },
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () async {
            try {
              await initializeControllerFuture;
              controller!.startImageStream(processImage);
            } catch (e) {
              print("Error starting camera stream: $e");
            }
          },
          child: const Icon(Icons.camera),
        ),
      ),
    );
  }
}
