package com.anchorauto;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ConfigScreen extends Screen {
    private static final int COLOR_OK = 0xFFE0E0E0;
    private static final int COLOR_ERROR = 0xFFFF5555;

    private final Screen parent;
    private boolean syncing;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("gui.anchorauto.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int max = AnchorAutoConfig.MAX_DELAY_TICKS;
        addRow(45, "gui.anchorauto.delay_place", 0, max, true,
                () -> AnchorAutoConfig.get().delayPlaceTicks, v -> AnchorAutoConfig.get().delayPlaceTicks = v);
        addRow(75, "gui.anchorauto.delay_charge", 0, max, true,
                () -> AnchorAutoConfig.get().delayChargeTicks, v -> AnchorAutoConfig.get().delayChargeTicks = v);
        addRow(105, "gui.anchorauto.delay_explode", 0, max, true,
                () -> AnchorAutoConfig.get().delayExplodeTicks, v -> AnchorAutoConfig.get().delayExplodeTicks = v);
        addRow(135, "gui.anchorauto.double_chance", 0, 100, false,
                () -> AnchorAutoConfig.get().doubleGlowstoneChancePercent, v -> AnchorAutoConfig.get().doubleGlowstoneChancePercent = v);

        int cx = this.width / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.anchorauto.reset"), b -> {
            AnchorAutoConfig.resetToDefaults();
            clearAndInit();
        }).dimensions(cx - 155, this.height - 40, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(cx + 5, this.height - 40, 150, 20).build());
    }

    private void addRow(int y, String key, int min, int max, boolean showMs, IntSupplier getter, IntConsumer setter) {
        int left = this.width / 2 - 140;
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, left + 208, y, 72, 20, Text.empty());
        field.setMaxLength(3);
        field.setText(String.valueOf(getter.getAsInt()));

        IntSlider slider = new IntSlider(left, y, 200, 20, key, showMs, min, max, getter.getAsInt(), v -> {
            setter.accept(v);
            syncing = true;
            field.setText(String.valueOf(v));
            field.setEditableColor(COLOR_OK);
            syncing = false;
        });

        field.setChangedListener(text -> {
            if (syncing) {
                return;
            }
            Integer value = parse(text, min, max);
            if (value == null) {
                field.setEditableColor(COLOR_ERROR);
            } else {
                field.setEditableColor(COLOR_OK);
                setter.accept(value);
                slider.setInt(value);
            }
        });

        addDrawableChild(slider);
        addDrawableChild(field);
    }

    private static Integer parse(String text, int min, int max) {
        try {
            int v = Integer.parseInt(text.trim());
            return (v >= min && v <= max) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("gui.anchorauto.hint"),
                this.width / 2, this.height - 62, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        AnchorAutoConfig.save();
        this.client.setScreen(parent);
    }

    private static final class IntSlider extends SliderWidget {
        private final String key;
        private final boolean showMs;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int width, int height, String key, boolean showMs,
                  int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height, Text.empty(), (initial - min) / (double) (max - min));
            this.key = key;
            this.showMs = showMs;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        int intValue() {
            return min + (int) Math.round(this.value * (max - min));
        }

        void setInt(int v) {
            this.value = (v - min) / (double) (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = intValue();
            setMessage(showMs ? Text.translatable(key, v, v * 50) : Text.translatable(key, v));
        }

        @Override
        protected void applyValue() {
            int v = intValue();
            onChange.accept(v);
            setInt(v); // auf ganze Werte einrasten
        }
    }
}
