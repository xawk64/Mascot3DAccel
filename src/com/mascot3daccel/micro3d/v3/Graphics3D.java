/*
 * MIT License
 * Copyright (c) 2026 Konstantin Zverev. All rights reserved.
 */

package com.mascot3daccel.micro3d.v3;

import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Material;
import javax.microedition.m3g.PolygonMode;
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
	private final Transform cameraToWorld;
	private final float[] matrixBuf;
	private final Appearance m3gAppearance;
	private final PolygonMode polygonMode;
	private final Material material;
	private final CompositingMode compositingMode;

	private Graphics boundGraphics;
	private int viewportWidth;
	private int viewportHeight;
	private int drawCenterX;
	private int drawCenterY;
	private boolean disposed;

	public Graphics3D() {
		m3g = javax.microedition.m3g.Graphics3D.getInstance();
		background = new Background();
		camera = new Camera();
		viewTransform = new Transform();
		cameraToWorld = new Transform();
		matrixBuf = new float[16];
		m3gAppearance = new Appearance();
		polygonMode = new PolygonMode();
		material = new Material();
		compositingMode = new CompositingMode();
		m3gAppearance.setPolygonMode(polygonMode);
		m3gAppearance.setMaterial(material);
		m3gAppearance.setCompositingMode(compositingMode);
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

	private void m3gTransformFromMC(AffineTrans mcTrans, Transform m3gTrans) {
		if (mcTrans == null) throw new NullPointerException();
		if (m3gTrans == null) throw new NullPointerException();

		float s = Util3D.FIXED_POINT_SCALE;
		float[] m = matrixBuf;

		m[0] = mcTrans.m00 / s;
		m[1] = mcTrans.m01 / s;
		m[2] = mcTrans.m02 / s;
		m[3] = mcTrans.m03 / s;
		m[4] = mcTrans.m10 / s;
		m[5] = mcTrans.m11 / s;
		m[6] = mcTrans.m12 / s;
		m[7] = mcTrans.m13 / s;
		m[8] = mcTrans.m20 / s;
		m[9] = mcTrans.m21 / s;
		m[10] = mcTrans.m22 / s;
		m[11] = mcTrans.m23 / s;
		m[12] = 0.0f;
		m[13] = 0.0f;
		m[14] = 0.0f;
		m[15] = 1.0f;

		m3gTrans.set(m);
	}

	private void setDrawCenter(FigureLayout layout, int x, int y) {
		if (layout != null) {
			drawCenterX = layout.centerX + x;
			drawCenterY = layout.centerY + y;
		} else {
			drawCenterX = x;
			drawCenterY = y;
		}
		if (Mascot3DAccel.halfResRender) {
			drawCenterY /= 2;
		}
	}

	private void updateM3GCamera(FigureLayout layout) {
		if (layout == null) throw new NullPointerException();

		float s = Util3D.FIXED_POINT_SCALE;
		float aspect = viewportHeight > 0
				? (float) viewportWidth / (float) viewportHeight
				: 1.0f;
		if (aspect <= 0.0f) aspect = 1.0f;

		int projection = layout.projectionMode;
		float nearF = layout.near / s;
		float farF = layout.far / s;

		if (projection == COMMAND_PERSPECTIVE_FOV) {
			if (nearF >= farF || nearF <= 0.0f) {
				nearF = 1.0f / s;
				farF = 32767.0f / s;
			}

			float fovRad;
			if (Mascot3DAccel.horizontalFovFix) {
				float fovHRad = layout.angle / s * (float) Math.PI;
				fovRad = (float) (2.0 * Math.atan(Math.tan(fovHRad * 0.5) / aspect));
			} else {
				fovRad = layout.angle / s * (float) Math.PI;
			}
			float fovyDeg = fovRad * 180.0f / (float) Math.PI;
			camera.setPerspective(fovyDeg, aspect, nearF, farF);
		} else if (projection == COMMAND_PERSPECTIVE_WH) {
			if (nearF >= farF || nearF <= 0.0f) {
				nearF = 1.0f / s;
				farF = 32767.0f / s;
			}

			float viewH = layout.perspectiveHeight / s;
			float viewW = layout.perspectiveWidth / s;
			if (viewH <= 0.0f || viewW <= 0.0f) {
				camera.setPerspective(45.0f, aspect, nearF, farF);
			} else {
				float fovRad = (float) (2.0 * Math.atan(viewH / (2.0 * nearF)));
				float fovyDeg = fovRad * 180.0f / (float) Math.PI;
				camera.setPerspective(fovyDeg, viewW / viewH, nearF, farF);
			}
		} else if (projection == COMMAND_PARALLEL_SIZE) {
			float viewH = layout.parallelHeight / s;
			float viewW = layout.parallelWidth / s;
			if (viewH <= 0.0f) viewH = 1.0f;
			if (viewW <= 0.0f) viewW = viewH * aspect;
			if (nearF <= 0.0f || farF <= nearF) {
				nearF = 1.0f / s;
				farF = 32767.0f / s;
			}
			camera.setParallel(viewH, viewW / viewH, nearF, farF);
		} else {
			float scaleX = layout.scaleX;
			float scaleY = layout.scaleY;
			if (Mascot3DAccel.halfResRender) scaleY /= 2;
			if (scaleX <= 0) scaleX = 512;
			if (scaleY <= 0) scaleY = 512;

			float viewH = viewportHeight * s / scaleY;
			float viewW = viewportWidth * s / scaleX;
			if (nearF <= 0.0f || farF <= nearF) {
				nearF = 1.0f / s;
				farF = 32767.0f / s;
			}
			camera.setParallel(viewH / s, viewW / viewH, nearF, farF);
		}

		AffineTrans view = layout.getAffineTrans();
		m3gTransformFromMC(view, viewTransform);
		cameraToWorld.set(viewTransform);
		cameraToWorld.invert();

		float cxOff = drawCenterX - viewportWidth * 0.5f;
		float cyOff = drawCenterY - viewportHeight * 0.5f;
		if (cxOff != 0.0f || cyOff != 0.0f) {
			float pixToWorldX;
			float pixToWorldY;
			if (projection == COMMAND_PARALLEL_SCALE || projection == COMMAND_PARALLEL_SIZE) {
				float scaleX = layout.scaleX;
				float scaleY = layout.scaleY;
				if (Mascot3DAccel.halfResRender) scaleY /= 2;
				if (scaleX <= 0) scaleX = 512;
				if (scaleY <= 0) scaleY = 512;
				pixToWorldX = s / scaleX;
				pixToWorldY = s / scaleY;
			} else {
				float fovRad = layout.angle / s * (float) Math.PI;
				float tanHalf = (float) Math.tan(fovRad * 0.5);
				if (tanHalf < 0.0001f) tanHalf = 0.0001f;
				pixToWorldY = nearF * 2.0f * tanHalf / viewportHeight;
				pixToWorldX = pixToWorldY;
			}
			cameraToWorld.postTranslate(-cxOff * pixToWorldX, cyOff * pixToWorldY, 0.0f);
		}

		m3g.setCamera(camera, cameraToWorld);
	}

	private Appearance getM3GAppearance(Texture tex, Effect3D effect) {
		if (effect == null) throw new NullPointerException();

		float s = Util3D.FIXED_POINT_SCALE;

		polygonMode.setPerspectiveCorrectionEnable(true);
		polygonMode.setCulling(PolygonMode.CULL_BACK);
		polygonMode.setWinding(PolygonMode.WINDING_CCW);

		boolean lighting = effect.getLight() != null && !Mascot3DAccel.noLighting;

		compositingMode.setDepthTestEnable(true);
		compositingMode.setDepthWriteEnable(true);
		compositingMode.setColorWriteEnable(true);
		compositingMode.setAlphaWriteEnable(false);

		if (effect.isTransparency() && !Mascot3DAccel.noBlending) {
			compositingMode.setBlending(CompositingMode.ALPHA);
		} else {
			compositingMode.setBlending(CompositingMode.REPLACE);
		}

		if (tex != null && tex.firstColorIsBlack) {
			compositingMode.setAlphaThreshold(1.0f / 255.0f);
		} else {
			compositingMode.setAlphaThreshold(0.0f);
		}

		if (tex != null) {
			m3gAppearance.setTexture(0, tex.getM3GTexture());
		} else {
			m3gAppearance.setTexture(0, null);
		}

		if (lighting) {
			Light mcLight = effect.getLight();
			float amb = mcLight.getAmbIntensity() / s;
			float dir = mcLight.getDirIntensity() / s;
			if (amb < 0.0f) amb = 0.0f;
			if (dir < 0.0f) dir = 0.0f;
			int ambRgb = rgbFromFloat(amb, amb, amb);
			int dirRgb = rgbFromFloat(dir, dir, dir);
			material.setColor(Material.AMBIENT, ambRgb);
			material.setColor(Material.DIFFUSE, dirRgb);
			material.setColor(Material.SPECULAR, 0x000000);
			material.setColor(Material.EMISSIVE, 0x000000);
			m3gAppearance.setMaterial(material);
		} else {
			m3gAppearance.setMaterial(null);
		}

		if (effect.getShadingType() == Effect3D.TOON_SHADING) {
			material.setShininess(0.0f);
		} else {
			material.setShininess(1.0f);
		}

		return m3gAppearance;
	}

	private static int rgbFromFloat(float r, float g, float b) {
		int ri = (int) (r * 255.0f);
		int gi = (int) (g * 255.0f);
		int bi = (int) (b * 255.0f);
		return (ri << 16) | (gi << 8) | bi;
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

		setDrawCenter(layout, x, y);
		updateM3GCamera(layout);

		// TODO: convert fixed-point vertices (divide by Util3D.FIXED_POINT_SCALE) into float[]
		// TODO: build VertexBuffer / IndexBuffer from figure.polyT3, polyT4, polyC3, polyC4
		// TODO: apply bone transforms via AffineTrans -> javax.microedition.m3g.Transform
		// TODO: m3g.render(vertexBuffer, indexBuffer, getM3GAppearance(figure.getTexture(), effect), transform)
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

		setDrawCenter(layout, x, y);
		updateM3GCamera(layout);

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

		setDrawCenter(layout, x, y);
		updateM3GCamera(layout);

		// TODO: interpret MascotCapsule command list opcodes and dispatch to renderPrimitives / renderFigure
	}
}
