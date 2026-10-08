package draylar.inmis;
/** Holds an item registered through the legacy Forge registry event. */
public final class LegacyRegistryEntry<T> {
    private T value;
    public LegacyRegistryEntry() {}
    public LegacyRegistryEntry(T value) { this.value = value; }
    public void set(T value) { if (this.value != null) throw new IllegalStateException("Item already registered"); this.value = value; }
    public boolean isPresent() { return value != null; }
    public T get() { if (value == null) throw new IllegalStateException("Item is not registered yet"); return value; }
}
