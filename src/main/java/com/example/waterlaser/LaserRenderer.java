package com.example.waterlaser;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Draws a tall translucent blue box (the "laser") above every cached water
 * position. The buffer/upload/draw plumbing follows the official Fabric 1.21.11
 * "Rendering in the World" guide, which is required on 1.21.11's render pipeline.
 */
public class LaserRenderer {
    private static final LaserRenderer INSTANCE = new LaserRenderer();

    public static LaserRenderer getInstance() {
        return INSTANCE;
    }

    // A vanilla pipeline: translucent, POSITION_COLOR quads, depth-tested (so
    // beams are correctly hidden behind terrain in front of them). To make the
    // beams visible THROUGH walls instead, register a custom pipeline based on
    // RenderPipelines.DEBUG_FILLED_SNIPPET with DepthTestFunction.NO_DEPTH_TEST.
    private static final RenderPipeline PIPELINE = RenderPipelines.DEBUG_FILLED_BOX;

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    public void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::onRender);
    }

    private void onRender(WorldRenderContext context) {
        if (!WaterLaserClient.isEnabled()) {
            return;
        }

        List<BlockPos> positions = WaterLaserClient.getBeamPositions();
        if (positions.isEmpty()) {
            return; // never build an empty buffer (buildOrThrow would throw)
        }

        Minecraft client = Minecraft.getInstance();
        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
        Matrix4fc pose = matrices.last().pose();

        for (BlockPos pos : positions) {
            float minX = pos.getX();
            float minZ = pos.getZ();
            float baseY = pos.getY() + 1f;                 // top face of the water block
            float topY = baseY + WaterLaserConfig.BEAM_HEIGHT;

            addBox(pose, buffer,
                    minX, baseY, minZ,
                    minX + 1f, topY, minZ + 1f,
                    WaterLaserConfig.RED, WaterLaserConfig.GREEN,
                    WaterLaserConfig.BLUE, WaterLaserConfig.ALPHA);
        }

        matrices.popPose();

        flushAndDraw(client, PIPELINE);
    }

    private static void addBox(Matrix4fc m, BufferBuilder buffer,
                               float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ,
                               float red, float green, float blue, float alpha) {
        // Front face
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Back face
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        // Left face
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Right face
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

        // Top face
        buffer.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);

        // Bottom face
        buffer.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        buffer.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private void flushAndDraw(Minecraft client, RenderPipeline pipeline) {
        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

        executeDraw(client, pipeline, builtBuffer, drawParameters, vertices, format);

        // Rotate the vertex buffer so we are less likely to use buffers the GPU is using.
        vertexBuffer.rotate();
        buffer = null;
    }

    private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(
                    () -> WaterLaserClient.MOD_ID + " beam buffer",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private static void executeDraw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer,
                                    MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            // Sort the quads since the beams are translucent.
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer =
                    RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> WaterLaserClient.MOD_ID + " beam pass",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            //noinspection ConstantValue
            renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
    }

    /** Called from GameRendererMixin on shutdown to free native + GPU buffers. */
    public void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
