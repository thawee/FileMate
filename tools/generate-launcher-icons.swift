import AppKit
import Foundation

struct Shape {
    let path: String
    let color: String
    let stroke: Double?
}

let folder = "M 29 48 Q 29 44 33 44 L 45 44 L 50 49 L 75 49 Q 79 49 79 53 L 79 73 Q 79 78 74 78 L 34 78 Q 29 78 29 73 Z"
let waves = [
    "M 38 33 Q 54 21 70 33",
    "M 45 39 Q 54 32 63 39"
]
let monogram = "M 43 68 L 43 57 L 54 65 L 65 57 L 65 68"
let colorShapes = [
    Shape(path: waves[0], color: "#67E8F9", stroke: 3.5),
    Shape(path: waves[1], color: "#67E8F9", stroke: 3.5),
    Shape(path: folder, color: "#818CF8", stroke: nil),
    Shape(path: "M 29 53 L 79 53 L 79 73 Q 79 78 74 78 L 34 78 Q 29 78 29 73 Z", color: "#6366F1", stroke: nil),
    Shape(path: monogram, color: "#FFFFFF", stroke: 3.5)
]
let monoShapes = [
    Shape(path: waves[0], color: "#FFFFFF", stroke: 3.5),
    Shape(path: waves[1], color: "#FFFFFF", stroke: 3.5),
    Shape(path: folder, color: "#FFFFFF", stroke: 3),
    Shape(path: monogram, color: "#FFFFFF", stroke: 3.5)
]
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let resources = root.appendingPathComponent("app/src/main/res")

func writeVector(_ shapes: [Shape], name: String) throws {
    let paths = shapes.map { shape in
        let style = shape.stroke.map {
            "android:fillColor=\"@android:color/transparent\" android:strokeColor=\"\(shape.color)\" android:strokeWidth=\"\($0)\" android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\""
        } ?? "android:fillColor=\"\(shape.color)\""
        return "    <path \(style) android:pathData=\"\(shape.path)\" />"
    }.joined(separator: "\n")
    let xml = """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp" android:height="108dp"
        android:viewportWidth="108" android:viewportHeight="108">
    \(paths)
    </vector>

    """
    try xml.write(to: resources.appendingPathComponent("drawable/\(name).xml"), atomically: true, encoding: .utf8)
}

func cgPath(_ data: String) -> CGPath {
    let tokens = data.split(separator: " ")
    let path = CGMutablePath()
    var index = 0
    func number() -> CGFloat {
        defer { index += 1 }
        return CGFloat(Double(tokens[index])!)
    }
    while index < tokens.count {
        let command = tokens[index]
        index += 1
        switch command {
        case "M": path.move(to: CGPoint(x: number(), y: number()))
        case "L": path.addLine(to: CGPoint(x: number(), y: number()))
        case "Q":
            let control = CGPoint(x: number(), y: number())
            path.addQuadCurve(to: CGPoint(x: number(), y: number()), control: control)
        case "Z": path.closeSubpath()
        default: fatalError("Unsupported path command \(command)")
        }
    }
    return path
}

func color(_ hex: String) -> CGColor {
    let value = UInt32(hex.dropFirst(), radix: 16)!
    return CGColor(red: CGFloat((value >> 16) & 255) / 255,
                   green: CGFloat((value >> 8) & 255) / 255,
                   blue: CGFloat(value & 255) / 255, alpha: 1)
}

func render(size: Int, round: Bool, shapes: [Shape], destination: URL) throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
                                 bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true,
                                 isPlanar: false, colorSpaceName: .deviceRGB,
                                 bytesPerRow: 0, bitsPerPixel: 0)!
    let context = NSGraphicsContext(bitmapImageRep: bitmap)!.cgContext
    context.translateBy(x: 0, y: CGFloat(size))
    context.scaleBy(x: CGFloat(size) / 108, y: -CGFloat(size) / 108)
    let bounds = CGRect(x: 0, y: 0, width: 108, height: 108)
    context.addPath(round ? CGPath(ellipseIn: bounds, transform: nil)
                         : CGPath(roundedRect: bounds, cornerWidth: 22, cornerHeight: 22, transform: nil))
    context.clip()
    context.setFillColor(color("#17203B"))
    context.fill(bounds)
    for shape in shapes {
        context.addPath(cgPath(shape.path))
        if let width = shape.stroke {
            context.setStrokeColor(color(shape.color))
            context.setLineWidth(width)
            context.setLineCap(.round)
            context.setLineJoin(.round)
            context.strokePath()
        } else {
            context.setFillColor(color(shape.color))
            context.fillPath()
        }
    }
    try bitmap.representation(using: .png, properties: [:])!.write(to: destination)
}

try writeVector(colorShapes, name: "ic_launcher_foreground")
try writeVector(monoShapes, name: "ic_launcher_monochrome")
for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    for (name, round) in [("ic_launcher", false), ("ic_launcher_round", true)] {
        let folder = resources.appendingPathComponent("mipmap-\(density)")
        try render(size: size, round: round, shapes: colorShapes, destination: folder.appendingPathComponent("\(name).png"))
        let old = folder.appendingPathComponent("\(name).webp")
        if FileManager.default.fileExists(atPath: old.path) { try FileManager.default.removeItem(at: old) }
    }
}
print("Generated adaptive foreground, monochrome artwork, and ten legacy PNG icons.")
if CommandLine.arguments.count == 3 && CommandLine.arguments[1] == "--preview" {
    let preview = URL(fileURLWithPath: CommandLine.arguments[2])
    try FileManager.default.createDirectory(at: preview, withIntermediateDirectories: true)
    try render(size: 432, round: true, shapes: colorShapes, destination: preview.appendingPathComponent("color.png"))
    try render(size: 432, round: true, shapes: monoShapes, destination: preview.appendingPathComponent("monochrome.png"))
}
