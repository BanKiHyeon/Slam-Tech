import 'dart:ui';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:vector_math/vector_math_64.dart' as math;

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '3D Cube Example',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  double _rotationX = 0;
  double _rotationY = 0;
  double _cameraX = 0;
  double _cameraY = 0;
  bool _isRightClick = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('3D Cube Example'),
      ),
      body: Center(
        child: Listener(
          onPointerDown: (event) {
            print(event);
            if (event.kind == PointerDeviceKind.mouse &&
                event.buttons == kSecondaryMouseButton) {
              _isRightClick = true;
            }
          },
          onPointerUp: (event) {
            if (event.kind == PointerDeviceKind.mouse &&
                event.buttons == kSecondaryMouseButton) {
              _isRightClick = false;
            }
          },
          onPointerMove: (event) {
            setState(() {
              if (_isRightClick) {
                _cameraX += event.delta.dx * 0.01;
                _cameraY += event.delta.dy * 0.01;
              } else {
                _rotationX += event.delta.dy * 0.01;
                _rotationY += event.delta.dx * 0.01;
              }
            });
          },
          child: CubePainterWidget(
            rotationX: _rotationX,
            rotationY: _rotationY,
            cameraX: _cameraX,
            cameraY: _cameraY,
          ),
        ),
      ),
    );
  }
}

class CubePainterWidget extends StatelessWidget {
  final double rotationX;
  final double rotationY;
  final double cameraX;
  final double cameraY;

  const CubePainterWidget({
    super.key,
    required this.rotationX,
    required this.rotationY,
    required this.cameraX,
    required this.cameraY,
  });

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: CubePainter(
        rotationX: rotationX,
        rotationY: rotationY,
        cameraX: cameraX,
        cameraY: cameraY,
      ),
      child: SizedBox(
        width: MediaQuery.of(context).size.width,
        height: MediaQuery.of(context).size.height,
        child: const SizedBox.expand(),
      ),
    );
  }
}

class CubePainter extends CustomPainter {
  final double rotationX;
  final double rotationY;
  final double cameraX;
  final double cameraY;

  CubePainter({
    required this.rotationX,
    required this.rotationY,
    required this.cameraX,
    required this.cameraY,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.blue
      ..style = PaintingStyle.stroke;

    final path = Path();

    final vertices = [
      math.Vector3(-1, -1, -1),
      math.Vector3(1, -1, -1),
      math.Vector3(1, 1, -1),
      math.Vector3(-1, 1, -1),
      math.Vector3(-1, -1, 1),
      math.Vector3(1, -1, 1),
      math.Vector3(1, 1, 1),
      math.Vector3(-1, 1, 1),
    ];

    final edges = [
      [0, 1],
      [1, 2],
      [2, 3],
      [3, 0],
      [4, 5],
      [5, 6],
      [6, 7],
      [7, 4],
      [0, 4],
      [1, 5],
      [2, 6],
      [3, 7],
    ];

    final cameraMatrix = math.Matrix4.identity()
      ..translate(cameraX, cameraY, -5.0)
      ..rotateX(rotationX)
      ..rotateY(rotationY);

    for (var edge in edges) {
      final p1 = cameraMatrix.transformed3(vertices[edge[0]]);
      final p2 = cameraMatrix.transformed3(vertices[edge[1]]);
      path.moveTo(p1.x * 100 + size.width / 2, -p1.y * 100 + size.height / 2);
      path.lineTo(p2.x * 100 + size.width / 2, -p2.y * 100 + size.height / 2);
    }

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }
}
