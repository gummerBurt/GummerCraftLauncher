/*
 * Copyright (C) 2021-2026 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.dialog;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * 28.11.2026 (власник: «"грати з другом" ... більш інтерактивним для дітей, з виразнішим текстом, іконками-емодзі»):
 * кольорові іконки в стилі емодзі, намальовані кодом. Справжні емодзі Java малює лише чорно-білим контуром (перевірено
 * 28.11 на Java лаунчера 17.0.21: шрифти Dialog, Segoe UI Emoji, Segoe UI Symbol), а {@link com.gummer.launcher.look.LookUi#body}
 * їх ще й вирізає. Малюється у квадраті 0..1, тож будь-який розмір і масштаб Windows без розмиття; файлів і ліцензій немає.
 */
public final class FunIcon implements Icon {

    public enum Kind {
        HOUSE, GLOBE, FRIENDS, MIC, PARTY, SHIELD, SEND, COPY, HAND, CONFUSED, PLAY, ESC, CHAT, UNLOCK, BOX, CHECK, NUMBER, BACK
    }

    private final Kind kind;
    private final int size;
    private final int number;

    public FunIcon(Kind kind, int size) {
        this(kind, size, 0);
    }

    private FunIcon(Kind kind, int size, int number) {
        this.kind = kind;
        this.size = size;
        this.number = number;
    }

    /** Кругла кольорова цифра кроку (кольори по колу: зелений, синій, помаранчевий, фіолетовий, рожевий). */
    public static FunIcon number(int n, int size) {
        return new FunIcon(Kind.NUMBER, size, n);
    }

    public static JLabel label(Kind kind, int size) {
        return new JLabel(new FunIcon(kind, size));
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    private static final Color SKIN = new Color(0xFFC983), SKIN_D = new Color(0xE0A15E), WHITE = Color.WHITE;
    static final Color[] NUM = {new Color(0x2EB872), new Color(0x3D8BFD), new Color(0xFF9F1C), new Color(0x9B5DE5), new Color(0xF15BB5)};

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(size, size);
        switch (kind) {
            case HOUSE: house(g); break;
            case GLOBE: globe(g); break;
            case FRIENDS: friends(g); break;
            case MIC: mic(g); break;
            case PARTY: party(g); break;
            case SHIELD: shield(g); break;
            case SEND: send(g); break;
            case COPY: copy(g); break;
            case HAND: hand(g); break;
            case CONFUSED: confused(g); break;
            case PLAY: play(g); break;
            case ESC: esc(g); break;
            case CHAT: chat(g); break;
            case UNLOCK: unlock(g); break;
            case BOX: box(g); break;
            case CHECK: check(g); break;
            case NUMBER: num(g); break;
            case BACK: back(g); break;
        }
        g.dispose();
    }

    private static void stroke(Graphics2D g, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private static void house(Graphics2D g) {
        g.setColor(new Color(0xFFE8C2));
        g.fill(new Rectangle2D.Double(.18, .45, .64, .45));
        Path2D roof = new Path2D.Double();
        roof.moveTo(.06, .5); roof.lineTo(.5, .1); roof.lineTo(.94, .5); roof.closePath();
        g.setColor(new Color(0xE63946));
        g.fill(roof);
        g.setColor(new Color(0x8D5A3B));
        g.fill(new RoundRectangle2D.Double(.42, .6, .17, .3, .06, .06));
        g.setColor(new Color(0x6EC1FF));
        g.fill(new RoundRectangle2D.Double(.23, .55, .14, .13, .04, .04));
        g.fill(new RoundRectangle2D.Double(.65, .55, .14, .13, .04, .04));
        g.setColor(new Color(0x7A4A2E));
        g.fill(new Rectangle2D.Double(.68, .18, .09, .2));
    }

    private static void globe(Graphics2D g) {
        g.setColor(new Color(0x3D8BFD));
        g.fill(new Ellipse2D.Double(.06, .06, .88, .88));
        g.setColor(new Color(0x43C463));
        Path2D a = new Path2D.Double();
        a.moveTo(.22, .25); a.curveTo(.35, .15, .5, .22, .47, .35); a.curveTo(.45, .48, .3, .45, .33, .6);
        a.curveTo(.35, .72, .22, .7, .17, .58); a.curveTo(.1, .45, .12, .32, .22, .25);
        g.fill(a);
        Path2D b = new Path2D.Double();
        b.moveTo(.6, .45); b.curveTo(.72, .38, .86, .45, .84, .6); b.curveTo(.82, .75, .66, .86, .6, .75);
        b.curveTo(.55, .66, .5, .52, .6, .45);
        g.fill(b);
        g.fill(new Ellipse2D.Double(.6, .16, .16, .1));
        g.setColor(new Color(255, 255, 255, 80));
        g.fill(new Ellipse2D.Double(.2, .12, .3, .16));
    }

    private static void person(Graphics2D g, double cx, double top, double w, Color shirt) {
        g.setColor(shirt);
        g.fill(new RoundRectangle2D.Double(cx - w / 2, top + w * .78, w, w * 1.05, w * .7, w * .7));
        g.setColor(SKIN);
        g.fill(new Ellipse2D.Double(cx - w * .32, top, w * .64, w * .64));
        g.setColor(new Color(0x5B3A29));
        g.fill(new Arc2D.Double(cx - w * .34, top - w * .03, w * .68, w * .5, 0, 180, Arc2D.CHORD));
    }

    private static void friends(Graphics2D g) {
        person(g, .33, .14, .4, new Color(0x3D8BFD));
        person(g, .68, .2, .38, new Color(0xFF9F1C));
    }

    private static void mic(Graphics2D g) {
        g.setColor(new Color(0x8A94A6));
        stroke(g, .07f);
        g.draw(new Arc2D.Double(.24, .2, .52, .5, 200, 140, Arc2D.OPEN));
        g.draw(new Line2D.Double(.5, .72, .5, .86));
        g.draw(new Line2D.Double(.34, .88, .66, .88));
        g.setColor(new Color(0xF15BB5));
        g.fill(new RoundRectangle2D.Double(.36, .06, .28, .5, .28, .28));
        g.setColor(new Color(0xFFB3DE));
        stroke(g, .03f);
        for (double yy = .18; yy < .5; yy += .09) g.draw(new Line2D.Double(.4, yy, .6, yy));
    }

    private static void party(Graphics2D g) {
        Path2D cone = new Path2D.Double();
        cone.moveTo(.08, .92); cone.lineTo(.3, .32); cone.lineTo(.68, .7); cone.closePath();
        g.setColor(new Color(0xFFC53D));
        g.fill(cone);
        Shape old = g.getClip();
        g.clip(cone);
        g.setColor(new Color(0xF15BB5));
        stroke(g, .07f);
        g.draw(new Line2D.Double(.0, .62, .5, .98));
        g.draw(new Line2D.Double(.12, .42, .62, .82));
        g.setClip(old);
        Color[] cs = {new Color(0x3D8BFD), new Color(0x2EB872), new Color(0xF15BB5), new Color(0xFF9F1C), new Color(0x9B5DE5)};
        double[][] dots = {{.55, .14}, {.78, .3}, {.88, .55}, {.66, .4}, {.45, .22}, {.9, .14}};
        for (int i = 0; i < dots.length; i++) {
            g.setColor(cs[i % cs.length]);
            g.fill(new Ellipse2D.Double(dots[i][0] - .05, dots[i][1] - .05, .1, .1));
        }
        stroke(g, .05f);
        g.setColor(new Color(0x9B5DE5));
        g.draw(new QuadCurve2D.Double(.42, .42, .55, .3, .68, .2));
        g.setColor(new Color(0x2EB872));
        g.draw(new QuadCurve2D.Double(.55, .58, .72, .55, .8, .42));
    }

    private static void shield(Graphics2D g) {
        Path2D s = new Path2D.Double();
        s.moveTo(.5, .05); s.lineTo(.88, .2); s.curveTo(.88, .55, .75, .8, .5, .95);
        s.curveTo(.25, .8, .12, .55, .12, .2); s.closePath();
        g.setColor(new Color(0x3D8BFD));
        g.fill(s);
        g.setColor(new Color(255, 255, 255, 60));
        Path2D h = new Path2D.Double();
        h.moveTo(.5, .05); h.lineTo(.5, .95); h.curveTo(.25, .8, .12, .55, .12, .2); h.closePath();
        g.fill(h);
        g.setColor(WHITE);
        stroke(g, .1f);
        Path2D ck = new Path2D.Double();
        ck.moveTo(.32, .5); ck.lineTo(.46, .64); ck.lineTo(.7, .36);
        g.draw(ck);
    }

    private static void send(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(.06, .45); p.lineTo(.94, .1); p.lineTo(.62, .92); p.lineTo(.46, .58); p.closePath();
        g.setColor(new Color(0x4CC9F0));
        g.fill(p);
        Path2D f = new Path2D.Double();
        f.moveTo(.46, .58); f.lineTo(.94, .1); f.lineTo(.4, .72); f.closePath();
        g.setColor(new Color(0x2B9FCB));
        g.fill(f);
    }

    private static void copy(Graphics2D g) {
        g.setColor(new Color(0xB07D4F));
        g.fill(new RoundRectangle2D.Double(.16, .12, .68, .82, .12, .12));
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(.24, .22, .52, .64, .06, .06));
        g.setColor(new Color(0x9AA5B1));
        g.fill(new RoundRectangle2D.Double(.34, .05, .32, .16, .08, .08));
        stroke(g, .05f);
        g.setColor(new Color(0xB8C2CC));
        for (double yy = .38; yy < .8; yy += .13) g.draw(new Line2D.Double(.33, yy, .67, yy));
    }

    private static void hand(Graphics2D g) {
        g.setColor(SKIN);
        double[][] fingers = {{.27, .22, .5}, {.41, .1, .6}, {.55, .12, .58}, {.69, .22, .48}};
        for (double[] f : fingers) g.fill(new RoundRectangle2D.Double(f[0], f[1], .13, f[2], .13, .13));
        g.fill(new RoundRectangle2D.Double(.27, .45, .55, .48, .3, .3));
        AffineTransform t = g.getTransform();
        g.rotate(-.7, .2, .62);
        g.fill(new RoundRectangle2D.Double(.08, .55, .32, .14, .14, .14));
        g.setTransform(t);
        g.setColor(SKIN_D);
        stroke(g, .025f);
        g.draw(new Line2D.Double(.41, .45, .41, .55));
        g.draw(new Line2D.Double(.55, .45, .55, .55));
        g.draw(new Line2D.Double(.69, .45, .69, .55));
    }

    private static void confused(Graphics2D g) {
        g.setColor(new Color(0xFFCC33));
        g.fill(new Ellipse2D.Double(.06, .06, .88, .88));
        g.setColor(new Color(0x5B3A29));
        g.fill(new Ellipse2D.Double(.3, .34, .1, .14));
        g.fill(new Ellipse2D.Double(.6, .34, .1, .14));
        stroke(g, .06f);
        Path2D m = new Path2D.Double();
        m.moveTo(.3, .7); m.curveTo(.4, .62, .5, .74, .7, .64);
        g.draw(m);
    }

    private static void roundSquare(Graphics2D g, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(.06, .06, .88, .88, .3, .3));
    }

    private static void play(Graphics2D g) {
        roundSquare(g, new Color(0x2EB872));
        Path2D p = new Path2D.Double();
        p.moveTo(.38, .27); p.lineTo(.74, .5); p.lineTo(.38, .73); p.closePath();
        g.setColor(WHITE);
        g.fill(p);
    }

    private static void esc(Graphics2D g) {
        g.setColor(new Color(0x5C6B80));
        g.fill(new RoundRectangle2D.Double(.04, .14, .92, .76, .22, .22));
        g.setColor(new Color(0xE9EEF5));
        g.fill(new RoundRectangle2D.Double(.04, .1, .92, .7, .22, .22));
        g.setColor(new Color(0x34405A));
        centerText(g, new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(.4f), "Esc", .5, .45);
    }

    private static void chat(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.append(new RoundRectangle2D.Double(.06, .1, .88, .62, .3, .3), false);
        p.moveTo(.25, .68); p.lineTo(.2, .92); p.lineTo(.45, .7); p.closePath();
        g.setColor(new Color(0x9B5DE5));
        g.fill(p);
        g.setColor(WHITE);
        g.fill(new Ellipse2D.Double(.24, .35, .12, .12));
        g.fill(new Ellipse2D.Double(.44, .35, .12, .12));
        g.fill(new Ellipse2D.Double(.64, .35, .12, .12));
    }

    private static void unlock(Graphics2D g) {
        g.setColor(new Color(0x9AA5B1));
        stroke(g, .09f);
        g.draw(new Arc2D.Double(.42, .06, .4, .46, 0, 180, Arc2D.OPEN));
        g.draw(new Line2D.Double(.82, .29, .82, .4));
        g.setColor(new Color(0xFFC53D));
        g.fill(new RoundRectangle2D.Double(.1, .4, .62, .52, .14, .14));
        g.setColor(new Color(0xB8860B));
        g.fill(new Ellipse2D.Double(.35, .56, .12, .12));
        g.fill(new Rectangle2D.Double(.38, .63, .06, .15));
    }

    private static void box(Graphics2D g) {
        Path2D top = new Path2D.Double();
        top.moveTo(.5, .08); top.lineTo(.92, .28); top.lineTo(.5, .48); top.lineTo(.08, .28); top.closePath();
        g.setColor(new Color(0xE2B47C));
        g.fill(top);
        Path2D l = new Path2D.Double();
        l.moveTo(.08, .28); l.lineTo(.5, .48); l.lineTo(.5, .94); l.lineTo(.08, .74); l.closePath();
        g.setColor(new Color(0xC88B4E));
        g.fill(l);
        Path2D r = new Path2D.Double();
        r.moveTo(.92, .28); r.lineTo(.5, .48); r.lineTo(.5, .94); r.lineTo(.92, .74); r.closePath();
        g.setColor(new Color(0xA86F3B));
        g.fill(r);
        g.setColor(new Color(0xF4D9B0));
        Path2D tape = new Path2D.Double();
        tape.moveTo(.29, .18); tape.lineTo(.71, .38); tape.lineTo(.71, .5); tape.lineTo(.29, .3); tape.closePath();
        g.fill(tape);
    }

    private static void check(Graphics2D g) {
        g.setColor(new Color(0x2EB872));
        g.fill(new Ellipse2D.Double(.04, .04, .92, .92));
        g.setColor(WHITE);
        stroke(g, .12f);
        Path2D ck = new Path2D.Double();
        ck.moveTo(.28, .52); ck.lineTo(.44, .68); ck.lineTo(.73, .36);
        g.draw(ck);
    }

    private static void back(Graphics2D g) {
        g.setColor(new Color(0x8A94A6));
        stroke(g, .12f);
        Path2D p = new Path2D.Double();
        p.moveTo(.6, .2); p.lineTo(.3, .5); p.lineTo(.6, .8);
        g.draw(p);
    }

    private void num(Graphics2D g) {
        g.setColor(NUM[Math.floorMod(number - 1, NUM.length)]);
        g.fill(new Ellipse2D.Double(.02, .02, .96, .96));
        g.setColor(WHITE);
        centerText(g, new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(.56f), String.valueOf(number), .5, .5);
    }

    /**
     * Текст точно по центру точки (cx, cy) за межами самих гліфів, а не за метриками шрифту. 28.11.2026 (власник: цифри в кружечках і
     * «Esc» з'їхали вліво): іконка малюється в одиничному квадраті, шрифт там менший за піксель, і {@code stringWidth} округлювався до
     * цілого, тож центр рахувався криво. Контур гліфів не округлюється і заливається як фігура.
     */
    static void centerText(Graphics2D g, Font f, String s, double cx, double cy) {
        java.awt.font.GlyphVector gv = f.createGlyphVector(new java.awt.font.FontRenderContext(null, true, true), s);
        java.awt.geom.Rectangle2D b = gv.getVisualBounds();
        g.fill(gv.getOutline((float) (cx - b.getCenterX()), (float) (cy - b.getCenterY())));
    }
}
