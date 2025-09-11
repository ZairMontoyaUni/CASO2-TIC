// File: Proceso.java
import java.io.*;
import java.util.*;

public class Proceso {
    public final int id;
    public final int TP;
    public final int NF, NC, NR, NP;
    private final List<Reference> refs;
    private int cursor = 0;

    // page -> frameId (-1 si no residente)
    private final Map<Integer, Integer> pageTable = new HashMap<>();

    // Estadísticas
    private int totalRefs = 0;
    private int pageFaults = 0;
    private int swaps = 0;
    private int hits = 0;

    private Proceso(int id, int TP, int NF, int NC, int NR, int NP, List<Reference> refs) {
        this.id = id;
        this.TP = TP;
        this.NF = NF;
        this.NC = NC;
        this.NR = NR;
        this.NP = NP;
        this.refs = refs;
        for (int p = 0; p < NP; p++) pageTable.put(p, -1);
    }

    // Factory: lee proc<i>.txt
    public static Proceso fromFile(String filename, int id) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String line;
        int TP = 0, NF = 0, NC = 0, NR = 0, NP = 0;
        List<Reference> refs = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("TP=")) TP = Integer.parseInt(line.substring(3));
            else if (line.startsWith("NF=")) NF = Integer.parseInt(line.substring(3));
            else if (line.startsWith("NC=")) NC = Integer.parseInt(line.substring(3));
            else if (line.startsWith("NR=")) NR = Integer.parseInt(line.substring(3));
            else if (line.startsWith("NP=")) NP = Integer.parseInt(line.substring(3));
            else {
                // Se espera: origin,page,offset,R/W
                String[] parts = line.split(",");
                if (parts.length >= 4) {
                    String origin = parts[0].trim();
                    int page = Integer.parseInt(parts[1].trim());
                    int offset = Integer.parseInt(parts[2].trim());
                    boolean write = parts[3].trim().equalsIgnoreCase("W");
                    refs.add(new Reference(page, offset, write, origin));
                }
            }
        }
        br.close();
        return new Proceso(id, TP, NF, NC, NR, NP, refs);
    }

    // --- API para Simulator ---
    public boolean hasNextReference() { return cursor < refs.size(); }
    public Reference peekNext() { return hasNextReference() ? refs.get(cursor) : null; }

    // Consumir la referencia solo cuando el acceso fue exitoso
    public Reference consumeNext() {
        if (!hasNextReference()) return null;
        totalRefs++;
        return refs.get(cursor++);
    }

    // Tabla de páginas (usada por MemoryManager)
    public void mapPageToFrame(int page, int frameId) { pageTable.put(page, frameId); }
    public void unmapPage(int page) { pageTable.put(page, -1); }
    public int frameOfPage(int page) { return pageTable.getOrDefault(page, -1); }

    // Estadísticas
    public void recordHit() { hits++; }
    public void recordFault() { pageFaults++; }
    public void recordSwap(int count) { swaps += count; }

    public void printStats() {
        System.out.println("Proceso " + id + " stats:");
        System.out.println("  Total refs: " + totalRefs);
        System.out.println("  Hits: " + hits);
        System.out.println("  Page faults: " + pageFaults);
        System.out.println("  SWAP accesses: " + swaps);
        double tasaFallos = totalRefs == 0 ? 0.0 : (100.0 * pageFaults / totalRefs);
        System.out.printf("  Tasa de fallos: %.2f%%\n", tasaFallos);
    }

    // Getter de estadísticas (útil para reasignación de marcos)
    public int getPageFaults() { return pageFaults; }
}
