package com.liangqu.gtuf;

import com.liangqu.gtuf.client.ClientProxy;
import com.liangqu.gtuf.common.CommonProxy;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file.
// Mixin 配置经 build.gradle 的 `mixin { config 'mixins.gtuf.json' }` 写入 jar 的
// MixinConfigs manifest 属性（正式发布）与 dev 运行参数，无需 @Mod 声明。
@Mod(GTUF_Core.MOD_ID)
public class GTUF_Core {
    public static final String MOD_ID = "gtuf";
    public static final String MOD_NAME = "GregTechForestryExtension";
    public static final Logger LOGGER = LogUtils.getLogger();


    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }


    public GTUF_Core() {
        DistExecutor.unsafeRunForDist(() -> ClientProxy::new, () -> CommonProxy::new);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
