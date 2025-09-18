// Simulator.java
// Soporta:
//  - java Simulator generate-annex        -> crea proc0.txt y proc1.txt (ejemplo anexo TP=128, 4x4 & 8x8)
//  - java Simulator simulate <nproc> <totalFrames>
// Compilar: javac Simulator.java
// Ejecutar: java Simulator simulate 2 8

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class Simulator {

    static class PageTableEntry {
        boolean loaded = false;
        int frameIndex = -1;
        long lastAccess = 0L;
    }

    static class Frame {
        int assignedToPid = -1;
        int virtualPage = -1;
    }

    static class Reference {
        String descr; // ex: "M1:[0-0]"
        int page;
        int offset;
        char action; // 'r' or 'w'
        Reference(String d, int p, int o, char a){ descr=d; page=p; offset=o; action=a; }
    }

    static class Proceso {
        int pid;
        int TP, NF, NC;
        int NR, NP;
        List<Reference> refs = new ArrayList<>();
        PageTableEntry[] pageTable;
        List<Integer> assignedFrames = new ArrayList<>();
        int nextRefIndex = 0;
        // stats
        int hits = 0;
        int faults = 0;
        int swaps = 0; // aquí hacemos swaps = faults por defecto
        Proceso(int pid){ this.pid=pid; }
    }

    static Frame[] frames;
    static long globalTime = 1L;
    static Proceso[] procesos;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: java Simulator generate-annex  OR  java Simulator simulate <nproc> <totalFrames>");
            return;
        }
        if ("generate-annex".equalsIgnoreCase(args[0])) {
            generateAnnex();
            System.out.println("Generados proc0.txt y proc1.txt (anexo).");
            return;
        }
        if ("simulate".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                System.err.println("simulate requiere: <nproc> <totalFrames>");
                return;
            }
            int nproc = Integer.parseInt(args[1]);
            int totalFrames = Integer.parseInt(args[2]);
            simulate(nproc, totalFrames);
            return;
        }
        System.err.println("Comando desconocido.");
    }

    // Generador simple para los ejemplos del anexo (TP=128; 4x4 y 8x8)
    static void generateAnnex() throws IOException {
        // proc0: TP=128 NF=4 NC=4 (3 matrices)
        generateProcFile("proc0.txt", 128, 4, 4);
        // proc1: TP=128 NF=8 NC=8
        generateProcFile("proc1.txt", 128, 8, 8);
    }

    static void generateProcFile(String filename, int TP, int NF, int NC) throws IOException {
        // Layout: matrices m1,m2,m3 consecutivas. cada int = 4 bytes.
        int elemsPerMatrix = NF * NC;
        int totalElems = elemsPerMatrix * 3;
        int bytesTotal = totalElems * 4;
        int NP = (bytesTotal + TP - 1) / TP;

        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.printf("TP=%d%n", TP);
            pw.printf("NF=%d%n", NF);
            pw.printf("NC=%d%n", NC);
            pw.printf("NR=%d%n", totalElems);
            pw.printf("NP=%d%n", NP);
            // generar referencias: por cada elemento k en 0..elemsPerMatrix-1 -> M1, M2, M3
            int baseM1 = 0;
            int baseM2 = elemsPerMatrix * 4; // bytes
            int baseM3 = elemsPerMatrix * 4 * 2;
            for (int k = 0; k < elemsPerMatrix; k++) {
                // M1
                int byteAddr1 = baseM1 + k * 4;
                int page1 = byteAddr1 / TP;
                int off1 = byteAddr1 % TP;
                pw.printf("M1:[%d-%d],%d,%d,r%n", k / NC, k % NC, page1, off1);
                // M2
                int byteAddr2 = baseM2 + k * 4;
                int page2 = byteAddr2 / TP;
                int off2 = byteAddr2 % TP;
                pw.printf("M2:[%d-%d],%d,%d,r%n", k / NC, k % NC, page2, off2);
                // M3 (usamos escritura para simular write)
                int byteAddr3 = baseM3 + k * 4;
                int page3 = byteAddr3 / TP;
                int off3 = byteAddr3 % TP;
                pw.printf("M3:[%d-%d],%d,%d,w%n", k / NC, k % NC, page3, off3);
            }
        }
    }

    static void simulate(int nproc, int totalFrames) throws IOException {
        if (totalFrames % nproc != 0) {
            System.err.println("Error: totalFrames debe ser múltiplo de nproc.");
            return;
        }
        // cargar procesos
        procesos = new Proceso[nproc];
        for (int i = 0; i < nproc; i++) {
            Proceso p = loadProcFile(i);
            if (p == null) { System.err.println("Fallo cargando proc" + i + ".txt"); return; }
            procesos[i] = p;
        }

        // inicializar frames y asignación equitativa
        frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) frames[i] = new Frame();

        int framesPerProc = totalFrames / nproc;
        for (int pid = 0; pid < nproc; pid++) {
            Proceso p = procesos[pid];
            System.out.printf("PROC %d == Leyendo archivo de configuración ==%n", pid);
            System.out.printf("PROC %d leyendo TP. Tam Páginas: %d%n", pid, p.TP);
            System.out.printf("PROC %d leyendo NF. Num Filas: %d%n", pid, p.NF);
            System.out.printf("PROC %d leyendo NC. Num Cols: %d%n", pid, p.NC);
            System.out.printf("PROC %d leyendo NR. Num Referencias: %d%n", pid, p.NR);
            System.out.printf("PROC %d leyendo NP. Num Paginas: %d%n", pid, p.NP);
            System.out.printf("PROC %d== Terminó de leer archivo de configuración ==%n", pid);

            for (int f = pid * framesPerProc; f < (pid + 1) * framesPerProc; f++) {
                frames[f].assignedToPid = pid;
                frames[f].virtualPage = -1;
                p.assignedFrames.add(f);
                System.out.printf("Proceso %d: recibe marco %d%n", pid, f);
            }
            p.pageTable = new PageTableEntry[p.NP];
            for (int j = 0; j < p.NP; j++) p.pageTable[j] = new PageTableEntry();
        }

        // crear cola round-robin con procesos que tienen referencias
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < nproc; i++) if (procesos[i].NR > 0) queue.add(i);

        // Simulación por turnos
        while (!queue.isEmpty()) {
            int pid = queue.poll();
            Proceso p = procesos[pid];
            System.out.printf("Turno proc: %d%n", pid);

            if (p.nextRefIndex >= p.NR) {
                // ya terminó
                System.out.println("========================");
                System.out.printf("Termino proc: %d%n", pid);
                System.out.println("========================");
                // remover y reasignar marcos
                for (int f : new ArrayList<>(p.assignedFrames)) {
                    System.out.printf("PROC %d removiendo marco: %d%n", pid, f);
                    frames[f].virtualPage = -1;
                }
                // reasignar a proceso con mayor fallos entre los que sigan en ejecución
                int bestPid = -1, maxFaults = -1;
                for (int j = 0; j < procesos.length; j++) {
                    if (j == pid) continue;
                    Proceso q = procesos[j];
                    if (q.nextRefIndex < q.NR) {
                        if (q.faults > maxFaults) { maxFaults = q.faults; bestPid = j; }
                    }
                }
                if (bestPid != -1) {
                    Proceso target = procesos[bestPid];
                    for (int f : new ArrayList<>(p.assignedFrames)) {
                        frames[f].assignedToPid = bestPid;
                        target.assignedFrames.add(f);
                        System.out.printf("PROC %d asignando marco nuevo %d%n", bestPid, f);
                    }
                    p.assignedFrames.clear();
                } else {
                    // no hay otro proceso en ejecución -> dejamos marcos libres
                    p.assignedFrames.clear();
                }
                continue;
            }

            // procesar la referencia actual (no avanzar índice si hubo fallo - demora 1 turno)
            int idx = p.nextRefIndex;
            System.out.printf("PROC %d analizando linea_: %d%n", pid, idx);
            Reference ref = p.refs.get(idx);
            boolean processed = false;
            PageTableEntry pte = p.pageTable[ref.page];

            if (pte.loaded) {
                p.hits++;
                pte.lastAccess = globalTime++;
                System.out.printf("PROC %d hits: %d%n", pid, p.hits);
                processed = true;
            } else {
                // falla
                p.faults++;
                // contabilizamos 1 swap por fallo (para ajustar al ejemplo del anexo).
                p.swaps++; // <-- si quieres 2 en reemplazo, modificar aquí.
                System.out.printf("PROC %d falla de pag: %d%n", pid, ref.page);
                // buscar frame libre en frames asignados
                Integer freeFrame = null;
                for (int f : p.assignedFrames) if (frames[f].virtualPage == -1) { freeFrame = f; break; }
                if (freeFrame != null) {
                    // cargar en freeFrame
                    frames[freeFrame].virtualPage = ref.page;
                    pte.loaded = true;
                    pte.frameIndex = freeFrame;
                    pte.lastAccess = globalTime++;
                } else {
                    // reemplazo LRU (entre páginas del proceso)
                    int victimPage = -1;
                    long minAccess = Long.MAX_VALUE;
                    for (int vp = 0; vp < p.NP; vp++) {
                        PageTableEntry e = p.pageTable[vp];
                        if (e.loaded && e.lastAccess < minAccess) {
                            minAccess = e.lastAccess;
                            victimPage = vp;
                        }
                    }
                    int chosenFrame;
                    if (victimPage == -1) {
                        // fallback: tomar primer frame asignado
                        chosenFrame = p.assignedFrames.get(0);
                    } else {
                        chosenFrame = p.pageTable[victimPage].frameIndex;
                        p.pageTable[victimPage].loaded = false;
                        p.pageTable[victimPage].frameIndex = -1;
                    }
                    frames[chosenFrame].virtualPage = ref.page;
                    pte.loaded = true;
                    pte.frameIndex = chosenFrame;
                    pte.lastAccess = globalTime++;
                }
                // la referencia NO se marca como procesada este turno (demora 1 turno)
                processed = false;
            }

            System.out.println("PROC " + pid + " envejecimiento");

            if (processed) {
                p.nextRefIndex++; // solo avanzar el índice si se procesó (hit)
            } else {
                // no avanzamos: el proceso volverá al final de la cola para reintentar la misma referencia
            }

            // reinsertar si aún tiene referencias
            if (p.nextRefIndex < p.NR) queue.add(pid);
        }

        // impresión final de estadísticas (formato anexo)
        for (int pid = 0; pid < procesos.length; pid++) {
            Proceso p = procesos[pid];
            System.out.printf("Proceso: %d%n", pid);
            System.out.printf("- Num referencias: %d%n", p.NR);
            System.out.printf("- Fallas: %d%n", p.faults);
            System.out.printf("- Hits: %d%n", p.hits);
            System.out.printf("- SWAP: %d%n", p.swaps);
            double tasaF = p.NR == 0 ? 0.0 : ((double)p.faults / p.NR);
            double tasaH = p.NR == 0 ? 0.0 : ((double)p.hits / p.NR);
            System.out.printf("- Tasa fallas: %.4f%n", tasaF);
            System.out.printf("- Tasa éxito: %.4f%n", tasaH);
        }
    }

    // Carga robusta: acepta formato compacto (primer token numeric) o anexo (TP=...)
    static Proceso loadProcFile(int pid) {
        String fn = "proc" + pid + ".txt";
        File f = new File(fn);
        if (!f.exists()) { System.err.println("No existe " + fn); return null; }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            List<String> lines = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
            if (lines.isEmpty()) return null;

            Proceso p = new Proceso(pid);

            // detectamos si el primer line contiene '=' -> formato anexo
            if (lines.get(0).contains("=")) {
                // parse header: buscar TP=, NF=, NC=, NR=, NP=
                for (String l : lines) {
                    if (l.startsWith("TP=")) p.TP = Integer.parseInt(l.substring(3).trim());
                    else if (l.startsWith("NF=")) p.NF = Integer.parseInt(l.substring(3).trim());
                    else if (l.startsWith("NC=")) p.NC = Integer.parseInt(l.substring(3).trim());
                    else if (l.startsWith("NR=")) p.NR = Integer.parseInt(l.substring(3).trim());
                    else if (l.startsWith("NP=")) p.NP = Integer.parseInt(l.substring(3).trim());
                }
                // referencias son las líneas que contienen comas (M1:...), encontrarlas en order
                Pattern pat = Pattern.compile("^([^,]+),\\s*([0-9]+),\\s*([0-9]+),\\s*([rRwW])");
                for (String l : lines) {
                    Matcher m = pat.matcher(l);
                    if (m.find()) {
                        String descr = m.group(1).trim();
                        int page = Integer.parseInt(m.group(2));
                        int offset = Integer.parseInt(m.group(3));
                        char action = Character.toLowerCase(m.group(4).charAt(0));
                        p.refs.add(new Reference(descr, page, offset, action));
                    }
                }
                // si NP no estaba o NR no estaba, rellenar:
                if (p.NR == 0) p.NR = p.refs.size();
                if (p.NP == 0) {
                    int maxPage = 0;
                    for (Reference r : p.refs) if (r.page > maxPage) maxPage = r.page;
                    p.NP = maxPage + 1;
                }
            } else {
                // formato compacto: primera línea contiene "TP NF NC NR NP" o similar numeric
                // intentamos parsear la primera línea con 5 ints
                String[] tok = lines.get(0).split("\\s+");
                if (tok.length >= 5) {
                    p.TP = Integer.parseInt(tok[0]);
                    p.NF = Integer.parseInt(tok[1]);
                    p.NC = Integer.parseInt(tok[2]);
                    p.NR = Integer.parseInt(tok[3]);
                    p.NP = Integer.parseInt(tok[4]);
                    // luego esperamos NR valores (dv) en siguientes líneas o en la misma línea
                    List<String> rest = new ArrayList<>();
                    // juntar todas las demás tokens
                    for (int i = 1; i < lines.size(); i++) {
                        String[] t2 = lines.get(i).split("\\s+");
                        for (String s : t2) if (!s.isEmpty()) rest.add(s);
                    }
                    // Si no hay suficiente cantidad, también podríamos tomar del primer line extra tokens
                    // parse each dv and compute page = dv / TP
                    for (int i = 0; i < Math.min(rest.size(), p.NR); i++) {
                        long dv = Long.parseLong(rest.get(i));
                        int page = (int)(dv / p.TP);
                        int offset = (int)(dv % p.TP);
                        p.refs.add(new Reference("NUM:" + i, page, offset, 'r'));
                    }
                } else {
                    System.err.println("Formato compacto inválido en " + fn);
                    return null;
                }
            }
            // asegurar NR y NP coherentes
            p.NR = p.refs.size();
            if (p.NP == 0) {
                int maxPage = 0;
                for (Reference r : p.refs) if (r.page > maxPage) maxPage = r.page;
                p.NP = maxPage + 1;
            }
            return p;
        } catch (Exception ex) {
            System.err.println("Error leyendo " + fn + ": " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }
}
