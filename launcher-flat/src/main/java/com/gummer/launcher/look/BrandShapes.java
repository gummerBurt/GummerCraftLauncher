/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.look;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Знаки Telegram і Discord векторно (20.11.2026, власник: «супер шакальна іконка»). Раніше це були вирізки з кнопок
 * 29x24 і 30x22 px, які на масштабі 125-150% розтягувались. Контури з Simple Icons (simpleicons.org, ліцензія CC0),
 * у Telegram лише літачок без кола, як на старих іконках.
 */
public final class BrandShapes {

    private static Shape telegram, discord;

    private BrandShapes() {
    }

    /** Літачок Telegram, вписаний у квадрат size x size з центром. */
    public static Shape telegram(double x, double y, double size) {
        if (telegram == null) {
            Path2D.Float p = new Path2D.Float(Path2D.WIND_EVEN_ODD);
            p.moveTo(16.906f, 7.224f);
            p.curveTo(17.006f, 7.222f, 17.227f, 7.247f, 17.371f, 7.364f);
            p.curveTo(17.418f, 7.405f, 17.458f, 7.455f, 17.487f, 7.51f);
            p.curveTo(17.516f, 7.566f, 17.535f, 7.627f, 17.542f, 7.689f);
            p.curveTo(17.558f, 7.782f, 17.578f, 7.995f, 17.562f, 8.161f);
            p.curveTo(17.382f, 10.059f, 16.6f, 14.663f, 16.202f, 16.788f);
            p.curveTo(16.034f, 17.688f, 15.703f, 17.989f, 15.382f, 18.018f);
            p.curveTo(14.686f, 18.083f, 14.157f, 17.558f, 13.482f, 17.116f);
            p.curveTo(12.426f, 16.423f, 11.829f, 15.992f, 10.804f, 15.316f);
            p.curveTo(9.619f, 14.536f, 10.387f, 14.106f, 11.062f, 13.406f);
            p.curveTo(11.239f, 13.222f, 14.309f, 10.429f, 14.369f, 10.176f);
            p.curveTo(14.376f, 10.144f, 14.383f, 10.026f, 14.313f, 9.964f);
            p.curveTo(14.243f, 9.902f, 14.139f, 9.923f, 14.064f, 9.94f);
            p.curveTo(13.958f, 9.964f, 12.271f, 11.08f, 9.003f, 13.285f);
            p.curveTo(8.523f, 13.615f, 8.09f, 13.775f, 7.701f, 13.765f);
            p.curveTo(7.273f, 13.757f, 6.449f, 13.524f, 5.836f, 13.325f);
            p.curveTo(5.084f, 13.08f, 4.487f, 12.951f, 4.539f, 12.536f);
            p.curveTo(4.566f, 12.32f, 4.864f, 12.099f, 5.432f, 11.873f);
            p.curveTo(8.93f, 10.349f, 11.262f, 9.344f, 12.43f, 8.859f);
            p.curveTo(15.762f, 7.473f, 16.455f, 7.232f, 16.906f, 7.224f);
            p.closePath();
            telegram = p;
        }
        return fit(telegram, x, y, size);
    }

    /** Знак Discord, вписаний у квадрат size x size з центром. */
    public static Shape discord(double x, double y, double size) {
        if (discord == null) {
            Path2D.Float p = new Path2D.Float(Path2D.WIND_EVEN_ODD);
            p.moveTo(20.317f, 4.37f);
            p.curveTo(18.761f, 3.656f, 17.119f, 3.146f, 15.432f, 2.855f);
            p.curveTo(15.422f, 2.853f, 15.411f, 2.853f, 15.401f, 2.855f);
            p.curveTo(15.391f, 2.858f, 15.382f, 2.862f, 15.373f, 2.868f);
            p.curveTo(15.365f, 2.875f, 15.358f, 2.883f, 15.353f, 2.892f);
            p.curveTo(15.142f, 3.267f, 14.909f, 3.756f, 14.745f, 4.141f);
            p.curveTo(12.9f, 3.865f, 11.065f, 3.865f, 9.258f, 4.141f);
            p.curveTo(9.095f, 3.748f, 8.852f, 3.267f, 8.641f, 2.892f);
            p.curveTo(8.635f, 2.883f, 8.628f, 2.875f, 8.62f, 2.869f);
            p.curveTo(8.612f, 2.863f, 8.603f, 2.858f, 8.593f, 2.856f);
            p.curveTo(8.583f, 2.854f, 8.572f, 2.853f, 8.562f, 2.855f);
            p.curveTo(6.875f, 3.146f, 5.233f, 3.655f, 3.677f, 4.37f);
            p.curveTo(3.67f, 4.373f, 3.664f, 4.376f, 3.659f, 4.381f);
            p.curveTo(3.653f, 4.386f, 3.649f, 4.391f, 3.645f, 4.397f);
            p.curveTo(0.533f, 9.046f, -0.319f, 13.58f, 0.099f, 18.058f);
            p.curveTo(0.1f, 18.069f, 0.104f, 18.079f, 0.109f, 18.089f);
            p.curveTo(0.114f, 18.099f, 0.122f, 18.107f, 0.13f, 18.114f);
            p.curveTo(2.183f, 19.622f, 4.172f, 20.537f, 6.123f, 21.143f);
            p.curveTo(6.133f, 21.146f, 6.144f, 21.147f, 6.154f, 21.146f);
            p.curveTo(6.165f, 21.145f, 6.175f, 21.142f, 6.184f, 21.136f);
            p.curveTo(6.193f, 21.131f, 6.201f, 21.124f, 6.207f, 21.116f);
            p.curveTo(6.669f, 20.485f, 7.081f, 19.82f, 7.433f, 19.122f);
            p.curveTo(7.438f, 19.112f, 7.441f, 19.101f, 7.442f, 19.091f);
            p.curveTo(7.442f, 19.08f, 7.44f, 19.069f, 7.436f, 19.059f);
            p.curveTo(7.432f, 19.049f, 7.426f, 19.04f, 7.419f, 19.033f);
            p.curveTo(7.411f, 19.025f, 7.402f, 19.019f, 7.392f, 19.016f);
            p.curveTo(6.739f, 18.768f, 6.118f, 18.466f, 5.52f, 18.124f);
            p.curveTo(5.509f, 18.117f, 5.5f, 18.108f, 5.493f, 18.097f);
            p.curveTo(5.486f, 18.087f, 5.483f, 18.074f, 5.482f, 18.062f);
            p.curveTo(5.481f, 18.049f, 5.483f, 18.036f, 5.489f, 18.025f);
            p.curveTo(5.494f, 18.013f, 5.502f, 18.003f, 5.512f, 17.996f);
            p.curveTo(5.638f, 17.902f, 5.764f, 17.804f, 5.884f, 17.704f);
            p.curveTo(5.891f, 17.699f, 5.899f, 17.694f, 5.908f, 17.691f);
            p.curveTo(5.917f, 17.688f, 5.926f, 17.687f, 5.935f, 17.688f);
            p.curveTo(5.944f, 17.688f, 5.953f, 17.69f, 5.962f, 17.694f);
            p.curveTo(9.889f, 19.487f, 14.142f, 19.487f, 18.023f, 17.694f);
            p.curveTo(18.031f, 17.69f, 18.04f, 17.688f, 18.05f, 17.687f);
            p.curveTo(18.059f, 17.686f, 18.068f, 17.687f, 18.077f, 17.69f);
            p.curveTo(18.086f, 17.693f, 18.094f, 17.698f, 18.101f, 17.703f);
            p.curveTo(18.222f, 17.802f, 18.347f, 17.902f, 18.474f, 17.996f);
            p.curveTo(18.484f, 18.003f, 18.492f, 18.013f, 18.498f, 18.025f);
            p.curveTo(18.503f, 18.036f, 18.506f, 18.049f, 18.505f, 18.061f);
            p.curveTo(18.504f, 18.074f, 18.5f, 18.086f, 18.494f, 18.097f);
            p.curveTo(18.487f, 18.108f, 18.478f, 18.117f, 18.468f, 18.123f);
            p.curveTo(17.869f, 18.473f, 17.243f, 18.771f, 16.595f, 19.015f);
            p.curveTo(16.585f, 19.019f, 16.575f, 19.025f, 16.568f, 19.032f);
            p.curveTo(16.56f, 19.04f, 16.554f, 19.049f, 16.55f, 19.059f);
            p.curveTo(16.547f, 19.069f, 16.545f, 19.08f, 16.545f, 19.091f);
            p.curveTo(16.546f, 19.101f, 16.549f, 19.112f, 16.554f, 19.122f);
            p.curveTo(16.914f, 19.82f, 17.326f, 20.484f, 17.779f, 21.115f);
            p.curveTo(17.785f, 21.123f, 17.793f, 21.131f, 17.802f, 21.136f);
            p.curveTo(17.811f, 21.141f, 17.821f, 21.145f, 17.832f, 21.146f);
            p.curveTo(17.842f, 21.147f, 17.853f, 21.146f, 17.863f, 21.143f);
            p.curveTo(19.824f, 20.537f, 21.813f, 19.621f, 23.865f, 18.114f);
            p.curveTo(23.874f, 18.107f, 23.882f, 18.099f, 23.887f, 18.09f);
            p.curveTo(23.892f, 18.08f, 23.896f, 18.07f, 23.897f, 18.059f);
            p.curveTo(24.397f, 12.882f, 23.059f, 8.385f, 20.348f, 4.398f);
            p.curveTo(20.345f, 4.392f, 20.34f, 4.386f, 20.335f, 4.381f);
            p.curveTo(20.33f, 4.376f, 20.324f, 4.372f, 20.317f, 4.37f);
            p.closePath();
            p.moveTo(8.02f, 15.331f);
            p.curveTo(6.837f, 15.331f, 5.863f, 14.245f, 5.863f, 12.912f);
            p.curveTo(5.863f, 11.579f, 6.819f, 10.493f, 8.02f, 10.493f);
            p.curveTo(9.231f, 10.493f, 10.196f, 11.589f, 10.177f, 12.912f);
            p.curveTo(10.177f, 14.246f, 9.221f, 15.331f, 8.02f, 15.331f);
            p.closePath();
            p.moveTo(15.995f, 15.331f);
            p.curveTo(14.812f, 15.331f, 13.838f, 14.245f, 13.838f, 12.912f);
            p.curveTo(13.838f, 11.579f, 14.793f, 10.493f, 15.995f, 10.493f);
            p.curveTo(17.206f, 10.493f, 18.171f, 11.589f, 18.152f, 12.912f);
            p.curveTo(18.152f, 14.246f, 17.206f, 15.331f, 15.995f, 15.331f);
            p.closePath();
            discord = p;
        }
        return fit(discord, x, y, size);
    }

    private static Shape fit(Shape s, double x, double y, double size) {
        Rectangle2D b = s.getBounds2D();
        double k = size / Math.max(b.getWidth(), b.getHeight());
        AffineTransform t = new AffineTransform();
        t.translate(x + (size - b.getWidth() * k) / 2, y + (size - b.getHeight() * k) / 2);
        t.scale(k, k);
        t.translate(-b.getX(), -b.getY());
        return t.createTransformedShape(s);
    }
}
