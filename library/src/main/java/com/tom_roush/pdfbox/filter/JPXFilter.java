/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDJPXColorSpace;

/**
 * Decompress data encoded using the wavelet-based JPEG 2000 standard,
 * reproducing the original data.
 *
 * Requires the JP2ForAndroid library to be available from com.gemalto.jp2:jp2-android:1.0.3, see
 * <a href="https://github.com/ThalesGroup/JP2ForAndroid">JP2ForAndroid</a>.
 *
 * @author John Hewson
 * @author Timo Boehme
 */
public final class JPXFilter extends Filter
{
    private static final int CACHE_SIZE = 1024;

    /**
     * {@inheritDoc}
     */
    @Override
    public DecodeResult decode(InputStream encoded, OutputStream decoded, COSDictionary
        parameters, int index, DecodeOptions options) throws IOException
    {
        DecodeResult result = new DecodeResult(new COSDictionary());
        result.getParameters().addAll(parameters);
        Bitmap image = readJPX(encoded, options, result);

        int arrLen = image.getWidth() * image.getHeight();
        int[] pixels = new int[arrLen];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

        // here we use a buffer to write batch to `decoded`, which makes it 10x faster than write byte one by one
        byte[] buffer = new byte[CACHE_SIZE * 3];
        int pos = 0;

        for (int i = 0; i < arrLen; i++)
        {
            if (pos + 3 >= buffer.length)
            {
                decoded.write(buffer, 0, pos);
                pos = 0;
            }
            int color = pixels[i];
            buffer[pos] = (byte)Color.red(color);
            buffer[pos + 1] = (byte)Color.green(color);
            buffer[pos + 2] = (byte)Color.blue(color);
            pos += 3;
        }
        decoded.write(buffer, 0, pos);
        return result;
    }

    @Override
    public DecodeResult decode(InputStream encoded, OutputStream decoded,
        COSDictionary parameters, int index) throws IOException
    {
        return decode(encoded, decoded, parameters, index, DecodeOptions.DEFAULT);
    }

    // try to read using JP2ForAndroid
    private Bitmap readJPX(InputStream input, DecodeOptions options, DecodeResult result) throws IOException
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * JPEG 2000 encoding requires optional dependency com.gemalto.jp2:jp2-android.
     * If not on classpath, throws IOException.
     */
    @Override
    protected void encode(InputStream input, OutputStream encoded, COSDictionary parameters)
        throws IOException
    {
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        byte[] jp2Bytes = encodeBitmapToJP2(bitmap);
        if (jp2Bytes == null) {
            throw new IOException(
                "JPEG 2000 encoding requires optional dependency com.gemalto.jp2:jp2-android. "
                + "Add it to your project or avoid creating PDFs with JPX images.");
        }
        IOUtils.copy(new ByteArrayInputStream(jp2Bytes), encoded);
        encoded.flush();
    }

    /**
     * Encodes bitmap to JP2 bytes using jp2-android JP2Encoder when available.
     *
     * @param bitmap the bitmap to encode
     * @return JP2 bytes, or null if jp2-android is not on classpath
     */
    private static byte[] encodeBitmapToJP2(Bitmap bitmap)
    {
        try {
            Class<?> encoderClass = Class.forName("com.gemalto.jp2.android.JP2Encoder");
            Object encoder = encoderClass.getConstructor(Bitmap.class).newInstance(bitmap);
            return (byte[]) encoderClass.getMethod("encode").invoke(encoder);
        }
        catch (Exception e) {
            return null;
        }
    }
}
