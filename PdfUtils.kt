package com.bjzb.ocrlibs_kt.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintManager
import com.bumptech.glide.Glide
import com.palmmob3.globallibs.AppInfo
import com.palmmob3.globallibs.ui.dialog.Tips
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.whzb.ktlibs.utils.FileUtils
import com.whzb.ktlibs.utils.ImageUtils
import com.whzb.ktlibs.utils.PdfPrintAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfUtils {
    suspend fun createPdfFromImages(
        context: Context,
        imageUris: List<Uri>,
        fileName: String = System.currentTimeMillis().toString(),
        pageWidth: Int = 595,  // A4 宽度，单位是像素（72dpi ≈ 8.3in）
        pageHeight: Int = 842,  // A4 高度
    ): File? {
        val outputPdfFile = FileUtils.createCacheFile(fileName, "pdf")

        val pdfDocument = PdfDocument()

        try {
            for ((index, uri) in imageUris.withIndex()) {
                val bitmap = OcrImageUtils.loadBitmap(context, uri)

                if (bitmap == null) {
                    // 某张图片加载失败，返回 null
                    pdfDocument.close()
                    return null
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)

                val canvas = page.canvas
                val scale = minOf(
                    pageWidth.toFloat() / bitmap.width,
                    pageHeight.toFloat() / bitmap.height
                )
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()

                val left = (pageWidth - scaledWidth) / 2f
                val top = (pageHeight - scaledHeight) / 2f

                canvas.drawBitmap(bitmap, left, top, null)
                pdfDocument.finishPage(page)
            }

            FileOutputStream(outputPdfFile).use { output ->
                pdfDocument.writeTo(output)
            }
            pdfDocument.close()
            return outputPdfFile

        } catch (e: Exception) {
            e.printStackTrace()
            try {
                pdfDocument.close()
            } catch (e: Exception) {

            }
            return null
        }
    }

    /**
     * 启动 Android 系统打印流程，打印指定 Uri 的 PDF 文件。
     *
     * @param context 当前上下文（例如 Activity 或 Application Context）。
     * @param pdfUri 要打印的 PDF 文件的 Uri。
     * @param jobName 打印任务的名称。
     */
    fun print(context: Context, pdfUri: Uri, jobName: String = "Document Print Job") {
        // 1. 获取 PrintManager
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

        // 2. 创建自定义的 PrintDocumentAdapter 来处理文件 Uri
        val printAdapter = PdfPrintAdapter(context, pdfUri)

        // 3. 调用 print() 启动打印流程
        printManager.print(jobName, printAdapter, null)
    }


    suspend fun createPdf(
        uris: List<Uri>,
        fileName: String,
    ): File? = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext null
        val outputPdfFile = FileUtils.createCacheFile(fileName, "pdf")
        PDDocument().use { document ->
            val pageSize = uris.size

            val options = uris.map { ImageUtils.getImageOptions(it) ?: throw RuntimeException("createPdf: imageOptions is null") }

            for (i in 0 until pageSize) {
                //创建PDF页
                val pdPage = PDPage(PDRectangle.A4)

                document.addPage(pdPage)

                //添加图片
                a4(i, uris, options, document, pdPage)

//            when (pdfLayout) {
//                PdfLayout.Original -> original(i, uris, options, document, pdPage)
//
//                PdfLayout.A4 -> a4(i, uris, options, document, pdPage)
//
//                PdfLayout.Left -> left(i, uris, options, document, pdPage)
//
//                PdfLayout.Two_One -> twoOne(i, uris, options, document, pdPage)
//
//                PdfLayout.One_Two -> oneTwo(i, uris, options, document, pdPage)
//
//                PdfLayout.Two_Two -> twoTwo(i, uris, options, document, pdPage)
//            }
            }

            document.save(outputPdfFile)
            return@withContext outputPdfFile
        }
        return@withContext null
    }

    private fun saveDocument(document: PDDocument?, password: String): File {
        val cachePdf = FileUtils.createCacheFile("${System.currentTimeMillis()}.pdf")

        val ap = AccessPermission()
        // 创建保护策略，只设置用户密码
        val spp = StandardProtectionPolicy("", password, ap)
        // 设置加密参数
        spp.encryptionKeyLength = 128 // 使用 128 位密钥
        spp.permissions = ap
        // 应用保护策略
        document?.protect(spp)

        document?.save(cachePdf)
        return cachePdf
    }

    private fun original(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        val uri = uris[index]
        val imageOptions = options[index]

        //获取图片尺寸
        var width = imageOptions.outWidth.toFloat()
        var height = imageOptions.outHeight.toFloat()
        // 获取页面尺寸
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height
        val widthRat: Float = width / pageWidth
        val heightRat: Float = height / pageHeight
        //矫正图片宽高
        if (widthRat > heightRat) {
            height /= widthRat
            width = pageWidth
        } else {
            width /= heightRat
            height = pageHeight
        }
        //坐标
        val x = (pageWidth / 2.0 - width / 2.0).toFloat()
        val y = (pageHeight / 2.0 - height / 2.0).toFloat()
        val pdImageXObject = getPDImageXObject(document, uri)
        val contentStream = PDPageContentStream(document, pdPage)
        contentStream.drawImage(pdImageXObject, x, y, width, height)
        contentStream.close()
    }

    private fun a4(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        // 定义边距大小 (单位为 PDF Point)
        val margin = 20.0f

        val uri = uris[index]
        val imageOptions = options[index]

        // 1. 获取图片尺寸
        var width = imageOptions.outWidth.toFloat()
        var height = imageOptions.outHeight.toFloat()

        // 2. 获取页面尺寸
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        // 3. 计算【带边距】的可用绘制区域
        val drawableWidth = pageWidth - 2 * margin // 页面宽度 - 左右边距
        val drawableHeight = pageHeight - 2 * margin // 页面高度 - 上下边距

        // 4. 计算图片相对于【可用区域】的缩放比例
        val widthRat: Float = width / drawableWidth
        val heightRat: Float = height / drawableHeight

        // 5. 矫正图片宽高 (适应模式：使用较大的比例进行缩小)
        if (widthRat > 1.0f || heightRat > 1.0f) { // 仅当图片大于可用区域时才缩小
            if (widthRat > heightRat) {
                // 宽度是限制性因素，将图片宽度设置为可用宽度
                height /= widthRat
                width = drawableWidth
            } else {
                // 高度是限制性因素，将图片高度设置为可用高度
                width /= heightRat
                height = drawableHeight
            }
        } else {
            // 如果图片小于可用区域，则保持原尺寸
            // 注意：如果需要放大，请调整此处的逻辑。
        }

        // 6. 计算居中坐标 (相对于整个页面)
        // 起始 x 坐标 = (页面宽度 / 2) - (图片绘制宽度 / 2)
        val x = (pageWidth / 2.0f - width / 2.0f)
        // 起始 y 坐标 = (页面高度 / 2) - (图片绘制高度 / 2)
        val y = (pageHeight / 2.0f - height / 2.0f)

        // 7. 绘制图片
        val pdImageXObject = getPDImageXObject(document, uri)

        // 应该在 try-catch-finally 块中处理 contentStream
        val contentStream = PDPageContentStream(document, pdPage)

        contentStream.drawImage(pdImageXObject, x, y, width, height)
        contentStream.close()
    }

    private fun left(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        // 定义边距大小 (单位为 PDF Point)
        val margin = 20.0f

        val uri = uris[index]
        val imageOptions = options[index]

        // 1. 获取图片原始尺寸
        var width = imageOptions.outWidth.toFloat()
        var height = imageOptions.outHeight.toFloat()

        // 2. 获取页面尺寸
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        // 3. 计算【带边距】的可用绘制区域
        val drawableWidth = pageWidth - 2 * margin // 页面宽度 - 左右边距
        val drawableHeight = pageHeight - 2 * margin // 页面高度 - 上下边距

        // 4. 计算图片相对于【可用区域】的缩放比例
        val widthRat: Float = width / drawableWidth
        val heightRat: Float = height / drawableHeight

        // 5. 矫正图片宽高 (适应模式：使用较大的比例进行缩小，确保图片在可用区域内)
        if (widthRat > 1.0f || heightRat > 1.0f) { // 仅当图片大于可用区域时才缩小
            if (widthRat > heightRat) {
                // 宽度是限制性因素
                height /= widthRat
                width = drawableWidth
            } else {
                // 高度是限制性因素
                width /= heightRat
                height = drawableHeight
            }
        } else {
            // 如果图片小于可用区域，保持原尺寸
        }

        // X 坐标：起始于左边距
        val x = margin

        // Y 坐标：起始于页面高度，减去上边距，再减去图片绘制高度 (PDF 坐标系)
        // 页面高度 - 上边距 - 图片绘制高度
        val y = pageHeight - margin - height

        // 7. 绘制图片
        val pdImageXObject = getPDImageXObject(document, uri)

        // 应该在 try-catch-finally 块中处理 contentStream
        val contentStream = PDPageContentStream(document, pdPage)

        contentStream.drawImage(pdImageXObject, x, y, width, height)
        contentStream.close()
    }

    private fun twoOne(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        val index1 = index * 2
        val index2 = index * 2 + 1

        // ---------------------- 1. 边距定义 ----------------------
        val pageMargin = 0f      // 页面边缘边距
        val internalMargin = 20.0f  // 两图之间间距

        val uri1 = uris.getOrNull(index1)
        val uri2 = uris.getOrNull(index2)
        val imageOptions1 = options.getOrNull(index1)
        val imageOptions2 = options.getOrNull(index2)

        // 2. 页面尺寸及可用区域计算
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        val availableWidth = pageWidth - 2 * pageMargin
        val totalVerticalMargin = 2 * pageMargin + internalMargin
        val maxAvailableHeight = (pageHeight - totalVerticalMargin) / 2.0f // 单图最大可用高度

        val contentStream = PDPageContentStream(document, pdPage)

        // ---------------------- 3. 图片 1 处理 (上半部分) ----------------------
        if (uri1 != null && imageOptions1 != null) {
            var w1 = imageOptions1.outWidth.toFloat()
            var h1 = imageOptions1.outHeight.toFloat()

            // 计算缩放比例 (适应模式)
            val ratioW1 = w1 / availableWidth
            val ratioH1 = h1 / maxAvailableHeight
            val scale1 = if (ratioW1 > ratioH1) ratioW1 else ratioH1

            var drawW1 = w1
            var drawH1 = h1
            if (scale1 > 1.0f) {
                drawW1 = w1 / scale1
                drawH1 = h1 / scale1
            }

            // Y 坐标 (垂直居中于上半部分)
            val topSectionAvailableCenter = pageHeight - pageMargin - maxAvailableHeight / 2.0f
            val y1 = topSectionAvailableCenter - (drawH1 / 2.0f)

            // X 坐标 (水平居中于整个页面宽度)
            val x1 = (pageWidth / 2.0f) - (drawW1 / 2.0f)

            // 绘制图片 1 (上半部分)
            val pdImageXObject1 = getPDImageXObject(document, uri1)
            contentStream.drawImage(pdImageXObject1, x1, y1, drawW1, drawH1)
        }


        // ---------------------- 4. 图片 2 处理 (下半部分) ----------------------
        if (uri2 != null && imageOptions2 != null) {
            var w2 = imageOptions2.outWidth.toFloat()
            var h2 = imageOptions2.outHeight.toFloat()

            // 计算缩放比例 (适应模式)
            val ratioW2 = w2 / availableWidth
            val ratioH2 = h2 / maxAvailableHeight
            val scale2 = if (ratioW2 > ratioH2) ratioW2 else ratioH2

            var drawW2 = w2
            var drawH2 = h2
            if (scale2 > 1.0f) {
                drawW2 = w2 / scale2
                drawH2 = h2 / scale2
            }

            // Y 坐标 (垂直居中于下半部分)
            val bottomSectionAvailableCenter = pageMargin + maxAvailableHeight / 2.0f
            val y2 = bottomSectionAvailableCenter - (drawH2 / 2.0f)

            // X 坐标 (水平居中于整个页面宽度)
            val x2 = (pageWidth / 2.0f) - (drawW2 / 2.0f)

            // 绘制图片 2 (下半部分)
            val pdImageXObject2 = getPDImageXObject(document, uri2)
            contentStream.drawImage(pdImageXObject2, x2, y2, drawW2, drawH2)
        }

        contentStream.close()
    }

    private fun oneTwo(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        val index1 = index * 2
        val index2 = index * 2 + 1

        // ---------------------- 1. 边距定义 ----------------------
        val pageMargin = 0f      // 页面边缘边距
        val internalMargin = 20.0f  // 两图之间间距

        val uri1 = uris.getOrNull(index1)
        val uri2 = uris.getOrNull(index2)
        val imageOptions1 = options.getOrNull(index1)
        val imageOptions2 = options.getOrNull(index2)

        // 2. 页面尺寸及可用区域计算
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        // 垂直可用高度 (PageHeight - 上下边距)
        val availableHeight = pageHeight - 2 * pageMargin

        // 水平分割计算
        val totalHorizontalMargin = 2 * pageMargin + internalMargin
        val maxAvailableWidth = (pageWidth - totalHorizontalMargin) / 2.0f // 单图最大可用宽度

        // 垂直中心线 Y 坐标 (图片居中的基准线)
        // 页面下边距 + 可用高度的一半
        val verticalCenter = pageMargin + availableHeight / 2.0f

        val contentStream = PDPageContentStream(document, pdPage)

        // ---------------------- 3. 图片 1 处理 (左半部分) ----------------------
        if (uri1 != null && imageOptions1 != null) {
            val currentImageOptions = imageOptions1

            var w1 = currentImageOptions.outWidth.toFloat()
            var h1 = currentImageOptions.outHeight.toFloat()

            // 计算缩放比例 (适应模式，基于 maxAvailableWidth 和 availableHeight)
            val ratioW1 = w1 / maxAvailableWidth
            val ratioH1 = h1 / availableHeight
            val scale1 = if (ratioW1 > ratioH1) ratioW1 else ratioH1

            var drawW1 = w1
            var drawH1 = h1
            if (scale1 > 1.0f) {
                drawW1 = w1 / scale1
                drawH1 = h1 / scale1
            }

            // X 坐标 (水平居中于左半部分)
            // 左半部分可用区域的中心 X 坐标
            val leftSectionAvailableCenter = pageMargin + maxAvailableWidth / 2.0f
            val x1 = leftSectionAvailableCenter - (drawW1 / 2.0f)

            // Y 坐标 (垂直居中于整个可用高度)
            val y1 = verticalCenter - (drawH1 / 2.0f)

            // 绘制图片 1
            val pdImageXObject1 = getPDImageXObject(document, uri1)
            contentStream.drawImage(pdImageXObject1, x1, y1, drawW1, drawH1)
        }

        // ---------------------- 4. 图片 2 处理 (右半部分) ----------------------
        if (uri2 != null && imageOptions2 != null) {
            val currentImageOptions = imageOptions2

            var w2 = currentImageOptions.outWidth.toFloat()
            var h2 = currentImageOptions.outHeight.toFloat()

            // 计算缩放比例 (适应模式)
            val ratioW2 = w2 / maxAvailableWidth
            val ratioH2 = h2 / availableHeight
            val scale2 = if (ratioW2 > ratioH2) ratioW2 else ratioH2

            var drawW2 = w2
            var drawH2 = h2
            if (scale2 > 1.0f) {
                drawW2 = w2 / scale2
                drawH2 = h2 / scale2
            }

            // X 坐标 (水平居中于右半部分)
            // 右半部分可用区域的中心 X 坐标
            val rightSectionAvailableCenter = pageWidth - pageMargin - maxAvailableWidth / 2.0f
            val x2 = rightSectionAvailableCenter - (drawW2 / 2.0f)

            // Y 坐标 (垂直居中于整个可用高度)
            val y2 = verticalCenter - (drawH2 / 2.0f)

            // 绘制图片 2
            val pdImageXObject2 = getPDImageXObject(document, uri2)
            contentStream.drawImage(pdImageXObject2, x2, y2, drawW2, drawH2)
        }

        contentStream.close()
    }

    private fun twoTwo(index: Int, uris: List<Uri>, options: List<BitmapFactory.Options>, document: PDDocument, pdPage: PDPage) {
        val index1 = index * 4
        val index2 = index * 4 + 1
        val index3 = index * 4 + 2
        val index4 = index * 4 + 3

        val uri1 = uris.getOrNull(index1)
        val uri2 = uris.getOrNull(index2)
        val uri3 = uris.getOrNull(index3)
        val uri4 = uris.getOrNull(index4)
        val imageOptions1 = options.getOrNull(index1)
        val imageOptions2 = options.getOrNull(index2)
        val imageOptions3 = options.getOrNull(index3)
        val imageOptions4 = options.getOrNull(index4)

        // ---------------------- 1. 边距定义 ----------------------
        val pageMargin = 0f      // 页面边缘（上、下、左、右）边距
        val internalMargin = 20.0f  // 图片之间的间距（水平和垂直）

        // 假设您已将四张图片的数据传入
        val imageSets = listOf(
            Pair(uri1, imageOptions1), // 左上 (1)
            Pair(uri2, imageOptions2), // 右上 (2)
            Pair(uri3, imageOptions3), // 左下 (3)
            Pair(uri4, imageOptions4)  // 右下 (4)
        )

        // 2. 页面尺寸及可用区域计算
        val mediaBox = pdPage.mediaBox
        val pageWidth = mediaBox.width
        val pageHeight = mediaBox.height

        // ---------------------- 2.1 基础计算 ----------------------
        // 水平总边距：左边距 + 间隙 + 右边距
        val totalHorizontalMargin = 2 * pageMargin + internalMargin
        // 垂直总边距：上边距 + 间隙 + 下边距
        val totalVerticalMargin = 2 * pageMargin + internalMargin

        // 单个格子（小区域）的最大可用宽度和高度
        val maxAvailableWidth = (pageWidth - totalHorizontalMargin) / 2.0f
        val maxAvailableHeight = (pageHeight - totalVerticalMargin) / 2.0f

        val contentStream = PDPageContentStream(document, pdPage)

        imageSets.forEachIndexed { index, (uri, options) ->

            // ---------------------- 3. 空值检查 ----------------------
            if (uri == null || options == null) {
                return@forEachIndexed // 如果数据为空，跳过本次循环，不绘制
            }

            // ---------------------- 4. 缩放计算 ----------------------
            var w = options.outWidth.toFloat()
            var h = options.outHeight.toFloat()

            // 计算缩放比例 (适应模式，基于格子尺寸)
            val ratioW = w / maxAvailableWidth
            val ratioH = h / maxAvailableHeight
            val scale = if (ratioW > ratioH) ratioW else ratioH

            var drawW = w
            var drawH = h
            if (scale > 1.0f) {
                drawW = w / scale
                drawH = h / scale
            }

            // ---------------------- 5. 坐标计算 (关键步骤) ----------------------

            // 行索引 (0: 上排, 1: 下排)
            val rowIndex = index / 2
            // 列索引 (0: 左列, 1: 右列)
            val colIndex = index % 2

            // X 轴起始位置计算
            // 基础偏移 = pageMargin + colIndex * (maxAvailableWidth + internalMargin)
            // 水平中心点 = 基础偏移 + maxAvailableWidth / 2.0f
            // 最终 X 坐标 = 水平中心点 - drawW / 2.0f
            val xCenter = pageMargin + colIndex * (maxAvailableWidth + internalMargin) + maxAvailableWidth / 2.0f
            val x = xCenter - (drawW / 2.0f)

            // Y 轴起始位置计算 (PDF 原点在左下角)
            // 基础偏移 = pageMargin + rowIndex * (maxAvailableHeight + internalMargin)
            // 垂直中心点 = 基础偏移 + maxAvailableHeight / 2.0f
            // 最终 Y 坐标 = 垂直中心点 - drawH / 2.0f
            // 注意：因为是两行，我们颠倒 rowIndex 来实现从上往下排布
            val effectiveRowIndex = 1 - rowIndex // 0->下排(1)，1->上排(0)

            val yCenter = pageMargin + effectiveRowIndex * (maxAvailableHeight + internalMargin) + maxAvailableHeight / 2.0f
            val y = yCenter - (drawH / 2.0f)

            // ---------------------- 6. 绘制 ----------------------
            val pdImageXObject = getPDImageXObject(document, uri)
            contentStream.drawImage(pdImageXObject, x, y, drawW, drawH)
        }

        contentStream.close()
    }

    private fun getPDImageXObject(
        document: PDDocument,
        uri: Uri,
    ): PDImageXObject {
        val bitmap = Glide.with(AppInfo.appContext)
            .asBitmap()
            .load(uri)
            .submit()
            .get()

        return JPEGFactory.createFromImage(document, bitmap)
    }

    /**
     * 使用 PDF 标准密码加密（RC4/128），加密后的 PDF 可用任意阅读器（系统、Adobe 等）输入密码打开。
     */
    suspend fun lock(file: File, password: String): File? = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            document = PDDocument.load(file)
            val ap = AccessPermission()
            val spp = StandardProtectionPolicy("", password, ap)
            spp.encryptionKeyLength = 128
            spp.permissions = ap
            document.protect(spp)
            val cacheFile = FileUtils.createCacheFile(file.name)
            document.save(cacheFile)
            return@withContext cacheFile
        } catch (e: Exception) {
            Tips.tip(com.palmmob3.langlibs.R.string.lb_processing_failed)
            return@withContext null
        } finally {
            document?.close()
        }
    }
}