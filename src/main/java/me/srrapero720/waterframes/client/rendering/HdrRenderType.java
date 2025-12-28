package me.srrapero720.waterframes.client.rendering;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.srrapero720.waterframes.client.rendering.core.ShaderCompat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.io.IOException;
import java.util.function.Function;

/**
 * WaterFrames HDR 渲染类型
 * 提供 HDR 到 SDR 色调映射的自定义 RenderType
 * 兼容 Iris/Oculus 光影模组
 */
@OnlyIn(Dist.CLIENT)
public class HdrRenderType extends RenderType {
    
    private static final ResourceLocation SHADER_ID = ResourceLocation.tryBuild("waterframes", "hdr_tonemap");
    
    @Nullable
    private static ShaderInstance hdrTonemapShader;
    private static float currentHdrMode = 0.0f;
    
    // 私有构造函数，不允许实例化
    private HdrRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, 
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
    
    /**
     * 注册 HDR shader
     */
    public static void registerShader(ResourceProvider provider) throws IOException {
        hdrTonemapShader = new ShaderInstance(provider, SHADER_ID.toString(), DefaultVertexFormat.BLOCK);
    }
    
    /**
     * 检查 HDR shader 是否可用
     * 当 Iris/Oculus 光影启用时，返回 false 以使用兼容的渲染路径
     */
    public static boolean isHdrShaderAvailable() {
        if (hdrTonemapShader == null) {
            return false;
        }
        // 当光影启用时，禁用自定义 HDR shader 以避免冲突
        if (ShaderCompat.areShadersActive()) {
            return false;
        }
        return true;
    }
    
    /**
     * 检查 HDR shader 是否已注册（不考虑光影状态）
     */
    public static boolean isHdrShaderRegistered() {
        return hdrTonemapShader != null;
    }
    
    /**
     * 设置当前 HDR 模式
     */
    public static void setHdrMode(int mode) {
        currentHdrMode = mode;
        if (hdrTonemapShader != null && !ShaderCompat.areShadersActive()) {
            var uniform = hdrTonemapShader.getUniform("HdrMode");
            if (uniform != null) {
                uniform.set(currentHdrMode);
            }
        }
    }
    
    /**
     * 获取 HDR shader 的 ShaderStateShard
     */
    private static final ShaderStateShard HDR_SHADER = new ShaderStateShard(() -> hdrTonemapShader);
    
    /**
     * 创建 HDR 渲染类型（带色调映射）
     */
    private static final Function<ResourceLocation, RenderType> HDR_TRANSLUCENT = Util.memoize((texture) -> {
        CompositeState state = CompositeState.builder()
                .setShaderState(HDR_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(false);
        return create("waterframes_hdr_translucent", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 
                      256, true, true, state);
    });
    
    /**
     * 标准 SDR 渲染类型（无色调映射）- 兼容 Iris/Oculus
     * 使用 entityTranslucentCull 以获得更好的光影兼容性
     */
    private static final Function<ResourceLocation, RenderType> SDR_TRANSLUCENT = Util.memoize((texture) -> {
        CompositeState state = CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false);
        return create("waterframes_sdr_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 
                      256, true, true, state);
    });
    
    /**
     * 光影兼容的渲染类型 - 使用 Minecraft 原生 shader
     */
    private static final Function<ResourceLocation, RenderType> SHADER_COMPAT_TRANSLUCENT = Util.memoize((texture) -> {
        // 使用 entityTranslucentCull，这是 Iris 最兼容的渲染类型之一
        return entityTranslucentCull(texture);
    });
    
    /**
     * 获取适合当前内容的 RenderType
     * 自动处理 Iris/Oculus 光影兼容性
     * 
     * @param texture 纹理资源位置
     * @param hdrMode HDR 模式 (0=SDR, 1=PQ, 2=HLG)
     * @return 合适的 RenderType
     */
    public static RenderType getDisplayRenderType(ResourceLocation texture, int hdrMode) {
        // 当光影启用时，使用兼容的渲染类型
        if (ShaderCompat.areShadersActive()) {
            return SHADER_COMPAT_TRANSLUCENT.apply(texture);
        }
        
        // 正常情况下，根据 HDR 模式选择渲染类型
        if (hdrMode != VideoPlayer.HDR_MODE_SDR && isHdrShaderAvailable()) {
            setHdrMode(hdrMode);
            return HDR_TRANSLUCENT.apply(texture);
        }
        return SDR_TRANSLUCENT.apply(texture);
    }
    
    /**
     * 释放 shader 资源
     */
    public static void close() {
        if (hdrTonemapShader != null) {
            hdrTonemapShader.close();
            hdrTonemapShader = null;
        }
    }
}
