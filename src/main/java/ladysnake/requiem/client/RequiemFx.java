/*
 * Requiem
 * Copyright (C) 2019 Ladysnake
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses>.
 */
package ladysnake.requiem.client;

import com.mojang.blaze3d.platform.GlStateManager;
import ladysnake.requiem.Requiem;
import ladysnake.requiem.api.v1.RequiemPlayer;
import ladysnake.satin.api.event.EntitiesPostRenderCallback;
import ladysnake.satin.api.event.ResolutionChangeCallback;
import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VisibleRegion;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;

import static ladysnake.requiem.client.FxHelper.impulse;
import static ladysnake.requiem.common.network.RequiemNetworking.*;

public final class RequiemFx implements EntitiesPostRenderCallback, ResolutionChangeCallback, ShaderEffectRenderCallback {
    public static final Identifier SPECTRE_SHADER_ID = Requiem.id("shaders/post/spectre.json");
    public static final Identifier FISH_EYE_SHADER_ID = Requiem.id("shaders/post/fish_eye.json");
    private static final float[] ETHEREAL_COLOR = {0.0f, 0.7f, 1.0f};

    public static final RequiemFx INSTANCE = new RequiemFx();
    public static final int PULSE_ANIMATION_TIME = 20;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final ManagedShaderEffect spectreShader = ShaderEffectManager.getInstance().manage(SPECTRE_SHADER_ID);
    private final ManagedShaderEffect fishEyeShader = ShaderEffectManager.getInstance().manage(FISH_EYE_SHADER_ID);
    private float accentColorR;
    private float accentColorG;
    private float accentColorB;
    @Nullable
    private GlFramebuffer framebuffer;

    private int fishEyeAnimation = -1;
    private int etherealAnimation = 0;
    private int pulseAnimation;
    private int pulseIntensity;
    /**
     * Incremented every tick for animations
     */
    private int ticks = 0;
    @Nullable
    private WeakReference<Entity> possessionTarget;

    void registerCallbacks() {
        ShaderEffectRenderCallback.EVENT.register(this);
        EntitiesPostRenderCallback.EVENT.register(this);
        ResolutionChangeCallback.EVENT.register(this);
        ClientTickCallback.EVENT.register(this::update);
    }

    public void update(@SuppressWarnings("unused") MinecraftClient client) {
        ++ticks;
        --etherealAnimation;
        if (--pulseAnimation < 0 && spectreShader.isInitialized()) {
            pulseIntensity = 1;
            spectreShader.setUniformValue("OverlayColor", ETHEREAL_COLOR[0], ETHEREAL_COLOR[1], ETHEREAL_COLOR[2]);
        }
        Entity possessed = getAnimationEntity();
        if (possessed != null) {
            if (--fishEyeAnimation == 2) {
                sendToServer(POSSESSION_REQUEST, createPossessionRequestBuffer(possessed));
            }
            if (!((RequiemPlayer) client.player).getRemnantState().isIncorporeal()) {
                this.possessionTarget = null;
            }
        }
    }

    public void onPossessionAck() {
        this.possessionTarget = null;
    }

    @Nullable
    public Entity getAnimationEntity() {
        return this.possessionTarget != null ? this.possessionTarget.get() : null;
    }

    public void beginFishEyeAnimation(Entity possessed) {
        this.fishEyeAnimation = 10;
        this.possessionTarget = new WeakReference<>(possessed);
    }

    public void beginEtherealAnimation() {
        this.etherealAnimation = 10;
        mc.player.world.playSound(mc.player, mc.player.x, mc.player.y, mc.player.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2, 0.6f);
    }

    public void playEtherealPulseAnimation(int intensity, float accentColorR, float accentColorG, float accentColorB) {
        this.pulseAnimation = PULSE_ANIMATION_TIME * intensity;
        this.accentColorR = accentColorR;
        this.accentColorG = accentColorG;
        this.accentColorB = accentColorB;
        spectreShader.setUniformValue("OverlayColor", accentColorR, accentColorG, accentColorB);
        this.pulseIntensity = intensity;
    }

    @Override
    public void renderShaderEffects(float tickDelta) {
        if (this.possessionTarget != null && this.possessionTarget.get() != null) {
            fishEyeShader.setUniformValue("Slider", (fishEyeAnimation - tickDelta) / 40 + 0.25f);
            fishEyeShader.render(tickDelta);
            if (this.possessionTarget != null && this.framebuffer != null) {
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
                this.framebuffer.draw(this.mc.window.getWidth(), this.mc.window.getHeight(), false);
                MinecraftClient.getInstance().worldRenderer.drawEntityOutlinesFramebuffer();
            }
        }
        boolean incorporeal = ((RequiemPlayer) mc.player).getRemnantState().isIncorporeal();
        if (incorporeal || this.etherealAnimation > 0 || this.pulseAnimation >= 0) {
            // 10 -> 1
            float zoom = Math.max(1, (etherealAnimation - tickDelta));
            float intensity = (incorporeal ? 0.6f : 0f) / zoom;
            float rayIntensity = 1.0f;
            spectreShader.setUniformValue("STime", (ticks + tickDelta) / 20f);
            // 10 -> 1
            if (pulseAnimation >= 0) {
                // 10 -> 0 => 0 -> 1
                float progress = 1 - Math.max(0, pulseAnimation - tickDelta) / (PULSE_ANIMATION_TIME * this.pulseIntensity);
                float value = impulse(8, progress);
                intensity += value;
                zoom += value / 2f;
                if (incorporeal) {
                    float r = ETHEREAL_COLOR[0] * (1 - value) + this.accentColorR * value;
                    float g = ETHEREAL_COLOR[1] * (1 - value) + this.accentColorG * value;
                    float b = ETHEREAL_COLOR[2] * (1 - value) + this.accentColorB * value;
                    spectreShader.setUniformValue("OverlayColor", r, g, b);
                } else {
                    rayIntensity = value;
                }
            }
            spectreShader.setUniformValue("Zoom", zoom);
            spectreShader.setUniformValue("RaysIntensity", rayIntensity);
            spectreShader.setUniformValue("SolidIntensity", intensity);
            spectreShader.render(tickDelta);
        }
    }

    @Override
    public void onEntitiesRendered(Camera camera, VisibleRegion frustum, float tickDelta) {
        Entity possessed = getAnimationEntity();
        if (possessed != null) {
            if (this.framebuffer == null) {
                this.framebuffer = new GlFramebuffer(mc.window.getWidth(), mc.window.getHeight(), true, MinecraftClient.IS_SYSTEM_MAC);
            }
            this.framebuffer.clear(MinecraftClient.IS_SYSTEM_MAC);
            GlStateManager.disableFog();
            this.framebuffer.beginWrite(false);
            this.mc.getEntityRenderManager().render(possessed, tickDelta, true);
            GlStateManager.enableLighting();
            GlStateManager.enableFog();
            GlStateManager.enableBlend();
            GlStateManager.enableColorMaterial();
            GlStateManager.enableDepthTest();
            GlStateManager.enableAlphaTest();
            this.mc.getFramebuffer().beginWrite(false);
        }
    }

    @Override
    public void onResolutionChanged(int newWidth, int newHeight) {
        if (this.framebuffer != null) {
            this.framebuffer.resize(newWidth, newHeight, MinecraftClient.IS_SYSTEM_MAC);
        }
    }
}