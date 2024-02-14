package com.example.playgroundcanvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.playgroundcanvas.ml.Model
import com.example.playgroundcanvas.ui.theme.PlaygroundCanvasTheme
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import android.graphics.Canvas as Canvas2

data class Line(
    val start: Offset,
    val end: Offset,
    val color: Color = Color.Black,
    val strokeWidth: Dp = 7.dp
)

@Preview(showBackground = true)
@Composable
fun App() {
    PlaygroundCanvasTheme {
        CanvasApp()
    }
}

@Composable
fun CanvasApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val lines = remember {
        mutableStateListOf<Line>()
    }
    
    var resultBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var result by remember {
        mutableStateOf("null")
    }

    var canvasSize: Size = Size.Zero

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

    }

    val openBrowser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val cameraIntent = getCameraIntent()

//    val drawable = ContextCompat.getDrawable(context, R.drawable.liane)
//    val drawableBitmap: Bitmap = (drawable as BitmapDrawable).bitmap

    Column(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .weight(0.7f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(true) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()

                            val line = Line(
                                start = change.position - dragAmount,
                                end = change.position
                            )

                            lines.add(line)
                        }
                    }
            ) {

                canvasSize = size
                lines.forEach { line ->
                    drawLine(
                        color = line.color,
                        start = line.start,
                        end = line.end,
                        strokeWidth = line.strokeWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(0.05f)
                .fillMaxWidth()
                .background(Color.Cyan),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Result: $result",
            )
        }
        Row(
            modifier = Modifier
                .weight(0.15f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = {
                    val canvasWidthInt = canvasSize.width.toInt()
                    val canvasHeightInt = canvasSize.height.toInt()
                    val bitmap = Bitmap.createBitmap(canvasWidthInt, canvasHeightInt, Bitmap.Config.ARGB_8888)
                    val drawScope = CanvasDrawScope()
                    drawScope.draw(
                        density = Density(context = context),
                        layoutDirection = LayoutDirection.Ltr,
                        canvas = androidx.compose.ui.graphics.Canvas(bitmap.asImageBitmap()),
                        size = Size(canvasWidthInt.toFloat(), canvasHeightInt.toFloat())
                    ) {
                        drawRect(color = Color.White, size = size)

                        lines.forEach { line ->
                            drawLine(
                                color = line.color,
                                start = line.start,
                                end = line.end,
                                strokeWidth = line.strokeWidth.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    resultBitmap = bitmap.trimBordersTwo().addWhiteSpaceWithPadding()

                    result = createPredictionFromBitmap(context, resultBitmap)
                    Log.d("bitmap", result)
                }
            ) {
                Text(text = "Predict")
            }
            Button(
                onClick = {
                    when (result) {
                        "circle" -> launchPhoneInterface(context)
                        "rectangle" -> launchEmailApp(context)
                        "square" -> openBrowser
                            .launch(createBrowserIntent("https://www.google.com"))
                        "triangle" -> takePicture.launch(cameraIntent)
                        else -> Toast.makeText(context,
                            "no detected shape",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
//                    launchPhoneInterface(context)
//                    launchEmailApp(context)
                }
            ) {
                Text(text = "Perform")
            }
            Button(
                onClick = {
                    lines.clear()
                    result = "null"
                    resultBitmap = null
                }
            ) {
                Text(text = "Reset")
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(0.1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            if (resultBitmap != null){
                Row {
                    Image(
                        painter = BitmapPainter(resultBitmap!!.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                    )
//                    Text(results)
                }
            } else {
                Text(
                    text = "Bitmap is null",
                    color = Color.White
                )
            }
        }
    }
}

fun createPredictionFromBitmap(context: Context, inputBitmap: Bitmap?):String {
    if (inputBitmap == null) {
        return ""
    }

    var tensorImage = TensorImage(DataType.FLOAT32)
    tensorImage.load(inputBitmap)

    val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(28,28, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    tensorImage = imageProcessor.process(tensorImage)

    val model = Model.newInstance(context)

    val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 28, 28, 3), DataType.FLOAT32)
    inputFeature0.loadBuffer(tensorImage.buffer)

    val outputs = model.process(inputFeature0)
    val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

    var maxIdx = 0

//    var results: String = ""

    outputFeature0.forEachIndexed { index, fl ->
        if (outputFeature0[maxIdx] < fl) {
            maxIdx = index
        }
//        results += " $fl"
    }

//    Toast.makeText(context, results, Toast.LENGTH_SHORT).show()

    model.close()

    val classNames = arrayOf("circle","rectangle","square","triangle")

    return classNames[maxIdx]
}

fun Bitmap.trimBordersTwo(): Bitmap {
    val borderColors = mutableListOf<Int>()

    // Collect border colors from the top and bottom rows
    for (x in 0 until width) {
        borderColors.add(getPixel(x, 0))
        borderColors.add(getPixel(x, height - 1))
    }

    // Collect border colors from the left and right columns (excluding corners)
    for (y in 1 until height - 1) {
        borderColors.add(getPixel(0, y))
        borderColors.add(getPixel(width - 1, y))
    }

    // Find the most frequent color in the border
    val mostFrequentColor = borderColors.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: android.graphics.Color.WHITE

    // Use the most frequent color to trim the borders
    var startX = 0
    loop@ for (x in 0 until width) {
        for (y in 0 until height) {
            if (getPixel(x, y) != mostFrequentColor) {
                startX = x
                break@loop
            }
        }
    }
    var startY = 0
    loop@ for (y in 0 until height) {
        for (x in 0 until width) {
            if (getPixel(x, y) != mostFrequentColor) {
                startY = y
                break@loop
            }
        }
    }
    var endX = width - 1
    loop@ for (x in endX downTo 0) {
        for (y in 0 until height) {
            if (getPixel(x, y) != mostFrequentColor) {
                endX = x
                break@loop
            }
        }
    }
    var endY = height - 1
    loop@ for (y in endY downTo 0) {
        for (x in 0 until width) {
            if (getPixel(x, y) != mostFrequentColor) {
                endY = y
                break@loop
            }
        }
    }
    val newWidth = endX - startX + 1
    val newHeight = endY - startY + 1

    return Bitmap.createBitmap(this, startX, startY, newWidth, newHeight)
}

fun Bitmap.addWhiteSpaceWithPadding(): Bitmap {
    val squareSize = maxOf(width, height)
    val paddingSize = (squareSize * 0.1).toInt() // 10% of the square size

    // Calculate the size of the new bitmap
    val newSize = squareSize + 2 * paddingSize

    // Create a new bitmap with dimensions equal to the new size
    val resultBitmap = Bitmap.createBitmap(newSize, newSize, config)

    // Create a canvas with the result bitmap
    val canvas = android.graphics.Canvas(resultBitmap)

    // Fill the canvas with white color
    canvas.drawColor(android.graphics.Color.WHITE)

    // Calculate the starting point for drawing the original bitmap
    val startX = paddingSize + (squareSize - width) / 2
    val startY = paddingSize + (squareSize - height) / 2

    // Draw the original bitmap on the canvas
    canvas.drawBitmap(this, startX.toFloat(), startY.toFloat(), null)

    return resultBitmap
}

fun Bitmap.addWhiteSpace(margin: Int = 0): Bitmap {
    val newWidth = width + 2 * margin
    val newHeight = height + 2 * margin
    val result = Bitmap.createBitmap(newWidth, newHeight, config)
    val canvas = Canvas2(result)
    canvas.drawColor(-1)  // Fill the entire canvas with white

    // Draw the original bitmap in the center of the canvas
    canvas.drawBitmap(this, margin.toFloat(), margin.toFloat(), null)

    return result
}

private fun launchPhoneInterface(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL)
    context.startActivity(intent)
}


fun launchEmailApp(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
    }

    context.startActivity(intent)
}

fun createBrowserIntent(url: String): Intent {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    return intent
}

fun getCameraIntent(): Intent {
    return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
}
