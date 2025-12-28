package me.srrapero720.waterframes.client.rendering.core;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;

/**
 * Shader 兼容性工具类
 * 检测 Iris/Oculus 等光影模组并提供兼容性处理
 */
@OnlyIn(Dist.CLIENT)
public final class ShaderCompat {
    
    private static final boolean IRIS_LOADED;
    private static final boolean OCULUS_LOADED;
    
    static {
        IRIS_LOADED = isModLoaded("iris");
        OCULUS_LOADED = isModLoaded("oculus");
    }
    
    private ShaderCompat() {}
    
    /**
     * 检查是否安装了光影模组 (Iris 或 Oculus)
     */
    public static boolean isShaderModPresent() {
        return IRIS_LOADED || OCULUS_LOADED;
    }
    
    /**
     * 检查 Iris 是否已加载
     */
    public static boolean isIrisLoaded() {
        return IRIS_LOADED;
    }
    
    /**
     * 检查 Oculus 是否已加载
     */
    public static boolean isOculusLoaded() {
        return OCULUS_LOADED;
    }
    
    /**
     * 检查光影是否当前启用
     * 注意：这需要在运行时检查，因为用户可以动态开关光影
     */
    public static boolean areShadersActive() {
        if (!isShaderModPresent()) {
            return false;
        }
        
        // 尝试通过反射检查 Iris/Oculus 的光影状态
        try {
            if (IRIS_LOADED) {
                return checkIrisShadersActive();
            } else if (OCULUS_LOADED) {
                return checkOculusShadersActive();
            }
        } catch (Exception e) {
            // 如果反射失败，假设光影未启用（宽松策略，允许渲染）
            return false;
        }
        
        return false;
    }
    
    private static boolean checkIrisShadersActive() {
        try {
            // Iris: net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            return (Boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Exception e) {
            // 备用方法
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Object pipeline = irisClass.getMethod("getPipelineManager").invoke(null);
                if (pipeline != null) {
                    Object currentPipeline = pipeline.getClass().getMethod("getPipeline").invoke(pipeline);
                    return currentPipeline != null;
                }
            } catch (Exception ignored) {}
            return false; // 检测失败时假设未启用
        }
    }
    
    private static boolean checkOculusShadersActive() {
        try {
            // Oculus 使用类似的 API
            Class<?> oculusApiClass = Class.forName("net.coderbot.iris.api.v0.IrisApi");
            Object instance = oculusApiClass.getMethod("getInstance").invoke(null);
            return (Boolean) oculusApiClass.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Exception e) {
            return false; // 检测失败时假设未启用
        }
    }
    
    private static boolean isModLoaded(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }
}
