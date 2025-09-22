package xland.mcplugin.fwds;

abstract class StageRunner implements Tickable {
    private int tick;
    protected final int maxTick;

    StageRunner(int maxTick) {
        this.maxTick = maxTick;
    }

    abstract void onTickEnds(I18n i18n);

    void reportTick(int tick, I18n i18n) {}

    @Override
    public void tick(I18n i18n) {
        if (tick < maxTick) {
            reportTick(++tick, i18n);
            if (tick == maxTick) {
                onTickEnds(i18n);
            }
        }
    }
}
