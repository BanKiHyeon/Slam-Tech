import 'package:opencv_dart/opencv.dart' as cv;
import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';

class OpenCVHelper {
  OpenCVHelper._privateConstructor();

  static final OpenCVHelper _instance = OpenCVHelper._privateConstructor();

  factory OpenCVHelper() {
    return _instance;
  }

  cv.Mat convertYUVToRGB(CameraImage image) {
    final int width = image.width;
    final int height = image.height;
    final int ySize = width * height;
    final int uvSize = (width / 2 * height / 2).toInt();

    final Uint8List yBytes = image.planes[0].bytes;
    final Uint8List uBytes = image.planes[1].bytes;
    final Uint8List vBytes = image.planes[2].bytes;

    final Uint8List yuvBytes = Uint8List(ySize + 2 * uvSize);
    yuvBytes.setRange(0, ySize, yBytes);
    yuvBytes.setRange(ySize, ySize + uvSize, uBytes);
    yuvBytes.setRange(ySize + uvSize, yuvBytes.length, vBytes);

    final cv.Mat yuvMat = cv.Mat.create(
        rows: height + height ~/ 2, cols: width, type: cv.MatType.CV_8UC1);
    yuvMat.data.setRange(0, yuvBytes.length, yuvBytes);

    final cv.Mat rgbMat = cv.cvtColor(yuvMat, cv.COLOR_YUV2RGB_I420);
    final cv.Mat rotatedMat = cv.rotate(rgbMat, cv.ROTATE_90_CLOCKWISE);
    return rotatedMat;
  }

  Future<cv.Mat> convertRGBToGray(cv.Mat im, {int count = 1000}) async {
    late cv.Mat gray;
    for (var i = 0; i < count; i++) {
      gray = await cv.cvtColorAsync(im, cv.COLOR_BGR2GRAY);
      //blur = await cv.gaussianBlurAsync(im, (7, 7), 2, sigmaY: 2);
      if (i != count - 1) {
        gray.dispose();
        //blur.dispose();
      }
    }
    return gray;
  }

  Future<cv.Mat> extractOrbFeatures(cv.Mat gray, cv.Mat frame) async {
    final detector = cv.ORB.create();
      final keyPoints = await detector.detectAsync(gray);

    for (var i = 0; i < keyPoints.length; i++) {
      final kp = keyPoints[i];
      cv.Scalar color;

      if (kp.response > 0.005) {
        color = cv.Scalar(255, 0, 0, 255);
      } else if (kp.response > 0.002) {
        color = cv.Scalar(0, 255, 0, 255);
      } else {
        color = cv.Scalar(0, 0, 255, 255);
      }
      cv.circle(frame, cv.Point(kp.x.toInt(), kp.y.toInt()), 5, color);
    }

    detector.dispose();
    keyPoints.dispose();

    return frame;
  }

  Future<cv.Mat> extractFastFeatures(cv.Mat gray, cv.Mat frame) async {
    final detector = cv.FastFeatureDetector.create(threshold: 50);
    final keyPoints = await detector.detectAsync(gray);

    for (var i = 0; i < keyPoints.length; i++) {
      final kp = keyPoints[i];
      cv.Scalar color;

      if (kp.response > 70) {
        color = cv.Scalar(255, 0, 0, 255);
      } else if (kp.response > 50) {
        color = cv.Scalar(0, 255, 0, 255);
      } else {
        color = cv.Scalar(0, 0, 255, 255);
      }
      cv.circle(frame, cv.Point(kp.x.toInt(), kp.y.toInt()), 5, color);
    }

    detector.dispose();
    keyPoints.dispose();

    return frame;
  }

  Uint8List convertMatToImage(cv.Mat mat) {
    return cv.imencode('.png', mat).$2;
  }
}
