// File: Reference.java
public class Reference {
    public final int page;      // número de página virtual
    public final int offset;    // offset dentro de la página (palabra/byte según convención)
    public final boolean write; // true si es escritura
    public final String origin; // texto origen para trazabilidad (ej. "M1[2,3]")

    public Reference(int page, int offset, boolean write, String origin) {
        this.page = page;
        this.offset = offset;
        this.write = write;
        this.origin = origin;
    }

    public boolean isWrite() { return write; }

    @Override
    public String toString() {
        return origin + "," + page + "," + offset + "," + (write ? "W" : "R");
    }
}
