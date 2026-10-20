import 'package:edge_detection/edge_detection.dart';

void main() async {
  bool? success = await EdgeDetection.detectEdge(
    "some_path.jpg",
    canUseGallery: true,
  );
  print(success);
}
