/*
 * MIT License
 * Copyright (c) 2026 Konstantin Zverev. All rights reserved.
 */

package com.mascot3daccel.micro3d.v3;

import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Transform;

public class Graphics3D {
	//Constants (MascotCapsule v3 API compatibility)
	public static final int COMMAND_LIST_VERSION_1_0 = 0xFE000001;
	public static final int COMMAND_END = 0x80000000;
	public static final int COMMAND_NOP = 0x81000000;
	public static final int COMMAND_FLUSH = 0x82000000;
	public static final int COMMAND_ATTRIBUTE = 0x83000000;
	public static final int COMMAND_CLIP = 0x84000000;
	public static final int COMMAND_CENTER = 0x85000000;
	public static final int COMMAND_TEXTURE_INDEX = 0x86000000;
	public static final int COMMAND_AFFINE_INDEX = 0x87000000;
	public static final int COMMAND_PARALLEL_SCALE = 0x90000000;
	public static final int COMMAND_PARALLEL_SIZE = 0x91000000;
	public static final int COMMAND_PERSPECTIVE_FOV = 0x92000000;
	public static final int COMMAND_PERSPECTIVE_WH = 0x93000000;
	public static final int COMMAND_AMBIENT_LIGHT = 0xa0000000;
	public static final int COMMAND_DIRECTION_LIGHT = 0xa1000000;
	public static final int COMMAND_THRESHOLD = 0xaf000000;

	public static final int ENV_ATTR_LIGHTING = 1;
	public static final int ENV_ATTR_SPHERE_MAP = 2;
	public static final int ENV_ATTR_TOON_SHADING = 4;
	public static final int ENV_ATTR_SEMI_TRANSPARENT = 8;

	public static final int PATTR_LIGHTING = 0x01;
	public static final int PATTR_SPHERE_MAP = 0x02;
	public static final int PATTR_COLORKEY = 0x10;
	public static final int PATTR_BLEND_NORMAL = 0x00;
	public static final int PATTR_BLEND_HALF = 0x20;
	public static final int PATTR_BLEND_ADD = 0x40;
	public static final int PATTR_BLEND_SUB = 0x60;

	public static final int PDATA_NORMAL_NONE = 0x0000;
	public static final int PDATA_NORMAL_PER_FACE = 0x0200;
	public static final int PDATA_NORMAL_PER_VERTEX = 0x0300;
	public static final int PDATA_COLOR_NONE = 0x0000;
	public static final int PDATA_COLOR_PER_COMMAND = 0x0400;
	public static final int PDATA_COLOR_PER_FACE = 0x0800;
	public static final int PDATA_TEXURE_COORD_NONE = 0x0000;
	public static final int PDATA_TEXURE_COORD = 0x3000;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_CMD = 0x1000;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_FACE = 0x2000;
	public static final int PDATA_POINT_SPRITE_PARAMS_PER_VERTEX = 0x3000;

	public static final int POINT_SPRITE_LOCAL_SIZE = 0;
	public static final int POINT_SPRITE_PIXEL_SIZE = 1;
	public static final int POINT_SPRITE_PERSPECTIVE = 0;
	public static final int POINT_SPRITE_NO_PERS = 2;

	public static final int PRIMITVE_POINTS = 0x01000000;
	public static final int PRIMITVE_LINES = 0x02000000;
	public static final int PRIMITVE_TRIANGLES = 0x03000000;
	public static final int PRIMITVE_QUADS = 0x04000000;
	public static final int PRIMITVE_POINT_SPRITES = 0x05000000;

	private final javax.microedition.m3g.Graphics3D m3g;
	private final Background background;
	private final Camera camera;
	private final Transform viewTransform;

	private Graphics boundGraphics;
	private int viewportWidth;
	private int viewportHeight;
	private boolean disposed;

	public Graphics3D() {
		m3g = javax.microedition.m3g.Graphics3D.getInstance();
		background = new Background();
		camera = new Camera();
		viewTransform = new Transform();
	}

	public final void dispose() {
		disposed = true;
		if (boundGraphics != null) {
			m3g.releaseTarget();
			boundGraphics = null;
		}
	}

	public final void bind(Graphics graphics) {
		if (disposed) return;
		if (graphics == null) throw new NullPointerException();
		if (boundGraphics != null) throw new IllegalStateException();

		boundGraphics = graphics;

		int clipX = graphics.getClipX();
		int clipY = graphics.getClipY();
		int clipW = graphics.getClipWidth();
		int clipH = graphics.getClipHeight();

		viewportWidth = Math.max(clipX + clipW, 0);
		viewportHeight = Math.max(clipY + clipH, 0);

		m3g.bindTarget(graphics, true, javax.microedition.m3g.Graphics3D.OVERWRITE);

		if (!Mascot3DAccel.doNotClear) {
			int color = Mascot3DAccel.fbClearColor;
			if (color == Mascot3DAccel.CLEAR_WITH_LAST_USED_COLOR) {
				color = graphics.getColor() & 0xffffff;
			}
			background.setColor(color);
			m3g.clear(background);
		}

		m3g.setViewport(clipX, clipY, clipW, clipH);
	}

	public final void release(Graphics graphics) {
		if (disposed) return;
		if (graphics == null) throw new NullPointerException();
		if (graphics != boundGraphics) throw new IllegalArgumentException();

		m3g.releaseTarget();
		boundGraphics = null;
	}

	public final void flush() {
		if (disposed) return;
		if (boundGraphics == null) throw new IllegalStateException();
		// TODO: flush deferred M3G draw batches (transparent pass, command list queue)
	}

	public final void drawFigure(
			Figure figure, int x, int y,
			FigureLayout layout, Effect3D effect
	) {
		renderFigure(figure, x, y, layout, effect);
		flush();
	}

	public final void renderFigure(
			Figure figure, int x, int y,
			FigureLayout layout, Effect3D effect
	) {
		if (disposed) return;
		if (boundGraphics == null) throw new IllegalStateException();
		if (figure == null || layout == null || effect == null) {
			throw new NullPointerException();
		}

		// TODO: convert fixed-point vertices (divide by Util3D.FIXED_POINT_SCALE) into float[]
		// TODO: build VertexBuffer / IndexBuffer from figure.polyT3, polyT4, polyC3, polyC4
		// TODO: apply bone transforms via AffineTrans -> javax.microedition.m3g.Transform
		// TODO: configure Camera from FigureLayout projection (parallel / perspective FOV)
		// TODO: set Appearance (Material, PolygonMode, Texture2D from Texture.getM3GTexture())
		// TODO: m3g.render(vertexBuffer, indexBuffer, appearance, transform)
	}

	public final void renderPrimitives(
			Texture texture, int x, int y,
			FigureLayout layout, Effect3D effect,
			int command, int numPrimitives,
			int[] vertexCoords, int[] normals,
			int[] textureCoords, int[] colors
	) {
		if (disposed) return;
		if (
			layout == null || effect == null ||
			vertexCoords == null || normals == null ||
			textureCoords == null || colors == null
		) {
			throw new NullPointerException();
		}
		if (numPrimitives <= 0 || numPrimitives > 255) {
			throw new IllegalArgumentException();
		}
		if ((command & 0x00ff0000) != 0) {
			throw new IllegalArgumentException();
		}
		if (boundGraphics == null) throw new IllegalStateException();

		// TODO: translate PRIMITVE_POINTS/LINES/TRIANGLES/QUADS/POINT_SPRITES into M3G draw calls
		// TODO: convert vertexCoords from fixed-point (>> 12) to float for VertexBuffer
	}

	public final void drawCommandList(
			Texture[] textures, int x, int y,
			FigureLayout layout, Effect3D effect,
			int[] commandList
	) {
		renderCommandList(textures, x, y, layout, effect, commandList);
		flush();
	}

	public final void renderCommandList(
			Texture[] textures, int x, int y,
			FigureLayout layout, Effect3D effect,
			int[] commandList
	) {
		if (disposed) return;
		if (boundGraphics == null) throw new IllegalStateException();
		if (textures == null || layout == null || effect == null || commandList == null) {
			throw new NullPointerException();
		}

		// TODO: interpret MascotCapsule command list opcodes and dispatch to renderPrimitives / renderFigure
	}
}
