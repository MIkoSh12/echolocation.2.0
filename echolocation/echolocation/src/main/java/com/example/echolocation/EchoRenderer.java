package com.example.echolocation;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Рисует контуры эха сквозь стены с помощью собственного render pipeline
 * (без теста глубины), по схеме из документации Fabric "Rendering in the World".
 */
public final class EchoRenderer {
    /** Без depth-state => рисуется поверх всего, в том числе сквозь стены. */
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(EcholocationClient.MOD_ID, "pipeline/echo_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .build());

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    // 2 МиБ = 32768 квадов (по 4 вершины x 16 байт); ниже лимит квадов с запасом.
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(() -> "Echolocation Buffer", 1 << 21);
    private static final int MAX_QUADS = 16000;

    /** Данные, снятые на этапе extraction и читаемые на этапе drawing. */
    private static volatile List<EchoManager.Layer> frame = List.of();

    private EchoRenderer() {}

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(EchoRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(EchoRenderer::renderAndDraw);
    }

    public static void close() {
        STAGED_BUFFER.close();
    }

    private static void extract(LevelExtractionContext context) {
        frame = EchoManager.snapshot();
    }

    private static void renderAndDraw(LevelRenderContext context) {
        List<EchoManager.Layer> layers = frame;
        if (layers.isEmpty()) return;

        RenderPipeline pipeline = FILLED_THROUGH_WALLS;
        VertexFormat formatBinding = pipeline.getVertexFormatBinding(0);
        assert formatBinding != null;

        PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
                formatBinding, primitive,
                primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

        int quads = fillBuffer(context, draw, layers);

        STAGED_BUFFER.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null && quads > 0) {
            draw(Minecraft.getInstance(), info, pipeline);
        }
        STAGED_BUFFER.endFrame();
    }

    private static int fillBuffer(LevelRenderContext context, StagedVertexBuffer.Draw draw, List<EchoManager.Layer> layers) {
        PoseStack poseStack = context.poseStack();
        Vec3 cam = context.levelState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4fc pose = poseStack.last().pose();
        VertexConsumer vb = STAGED_BUFFER.getVertexBuilder(draw);
        Direction[] dirs = Direction.values();
        int quads = 0;

        for (EchoManager.Layer layer : layers) {
            // Контуры блоков: только грани, смотрящие в воздух. Голубой.
            float blockAlpha = 0.15f + 0.30f * layer.fade();
            for (EchoPulse.ShellBlock b : layer.shell()) {
                for (Direction d : dirs) {
                    if ((b.mask() & (1 << d.ordinal())) == 0) continue;
                    if (quads >= MAX_QUADS) break;
                    face(vb, pose, d, b.x(), b.y(), b.z(), b.x() + 1, b.y() + 1, b.z() + 1,
                            0.2f, 0.85f, 1.0f, blockAlpha);
                    quads++;
                }
            }
            // Мобы: полупрозрачный оранжевый бокс.
            float mobAlpha = 0.20f + 0.40f * layer.fade();
            for (EchoPulse.MobBox m : layer.mobs()) {
                for (Direction d : dirs) {
                    if (quads >= MAX_QUADS) break;
                    face(vb, pose, d, m.minX(), m.minY(), m.minZ(), m.maxX(), m.maxY(), m.maxZ(),
                            1.0f, 0.55f, 0.1f, mobAlpha);
                    quads++;
                }
            }
        }

        poseStack.popPose();
        return quads;
    }

    /** Одна грань бокса (порядок вершин как в примере из документации Fabric). */
    private static void face(VertexConsumer b, Matrix4fc m, Direction dir,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float bl, float a) {
        switch (dir) {
            case SOUTH -> { // +Z
                v(b, m, x0, y0, z1, r, g, bl, a); v(b, m, x1, y0, z1, r, g, bl, a);
                v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x0, y1, z1, r, g, bl, a);
            }
            case NORTH -> { // -Z
                v(b, m, x1, y0, z0, r, g, bl, a); v(b, m, x0, y0, z0, r, g, bl, a);
                v(b, m, x0, y1, z0, r, g, bl, a); v(b, m, x1, y1, z0, r, g, bl, a);
            }
            case WEST -> { // -X
                v(b, m, x0, y0, z0, r, g, bl, a); v(b, m, x0, y0, z1, r, g, bl, a);
                v(b, m, x0, y1, z1, r, g, bl, a); v(b, m, x0, y1, z0, r, g, bl, a);
            }
            case EAST -> { // +X
                v(b, m, x1, y0, z1, r, g, bl, a); v(b, m, x1, y0, z0, r, g, bl, a);
                v(b, m, x1, y1, z0, r, g, bl, a); v(b, m, x1, y1, z1, r, g, bl, a);
            }
            case UP -> { // +Y
                v(b, m, x0, y1, z1, r, g, bl, a); v(b, m, x1, y1, z1, r, g, bl, a);
                v(b, m, x1, y1, z0, r, g, bl, a); v(b, m, x0, y1, z0, r, g, bl, a);
            }
            case DOWN -> { // -Y
                v(b, m, x0, y0, z0, r, g, bl, a); v(b, m, x1, y0, z0, r, g, bl, a);
                v(b, m, x1, y0, z1, r, g, bl, a); v(b, m, x0, y0, z1, r, g, bl, a);
            }
        }
    }

    private static void v(VertexConsumer b, Matrix4fc m, float x, float y, float z,
                          float r, float g, float bl, float a) {
        b.addVertex(m, x, y, z).setColor(r, g, bl, a);
    }

    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        GpuTextureView colorTexture = mainTarget.getColorTextureView();
        assert colorTexture != null;

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> EcholocationClient.MOD_ID + " echo rendering",
                        colorTexture, Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }
}
