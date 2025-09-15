// Simulator.java
// Compilar: javac Simulator.java
// Ejecutar: java Simulator simulate <nproc> <totalFrames>
import java.io.*;
import java.util.*;

public class App {

    // ---------------------------
    // Estructuras principales
    // ---------------------------
    static class PageTableEntry {
        boolean loaded = false;
        int frameIndex = -1;
        long lastAccess = 0L; // para LRU
    }

    static class Frame {
        int assignedToPid;    // a qué proceso está asignado (marco "reservado" para ese proceso)
        int virtualPage = -1; // qué página virtual está en este frame (-1 = libre)
    }

    static class Proceso {
        int pid;
        int TP, NF, NC, NR, NP;
        List<Long> dvList;
        PageTableEntry[] pageTable;
        List<Integer> assignedFrames; // indices de frames físicos asignados
        int nextRefIndex = 0;

        // Stats
        int hits = 0;
        int faults = 0;
        int swaps = 0; // accesos a SWAP contados conforme al enunciado (1 o 2 por fallo)
        Proceso(int pid) { this.pid = pid; }
    }

    // ---------------------------
    // Variables globales del simulador
    // ---------------------------
    static Frame[] frames;
    static long globalTime = 1L; // contador para LRU (usamos long)
    static Proceso[] procesos;

    // ---------------------------
    // Main
    // ---------------------------
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Uso:");
            System.out.println("  java Simulator simulate <nproc> <totalFrames>");
            return;
        }

        String cmd = args[0];
        if ("simulate".equalsIgnoreCase(cmd)) {
            if (args.length != 3) {
                System.err.println("simulate requiere 2 argumentos: <nproc> <totalFrames>");
                return;
            }
            int nproc = Integer.parseInt(args[1]);
            int totalFrames = Integer.parseInt(args[2]);
            simulate(nproc, totalFrames);
        } else {
            System.err.println("Comando desconocido: " + cmd);
        }
    }

    // ---------------------------
    // Simulación (Opción 2)
    // ---------------------------
    static void simulate(int nproc, int totalFrames) throws IOException {
        // Validaciones iniciales
        if (totalFrames % nproc != 0) {
            System.err.println("Error: totalFrames debe ser múltiplo de nproc.");
            return;
        }

        // Cargar procesos desde proc0.txt ... proc{nproc-1}.txt
        procesos = new Proceso[nproc];
        for (int i = 0; i < nproc; i++) {
            Proceso p = loadProcFile(i);
            if (p == null) {
                System.err.println("No se pudo leer proc" + i + ".txt");
                return;
            }
            procesos[i] = p;
        }

        // Inicializar frames y asignar equitativamente
        frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) frames[i] = new Frame();

        int framesPerProc = totalFrames / nproc;
        for (int pid = 0; pid < nproc; pid++) {
            Proceso p = procesos[pid];
            p.assignedFrames = new ArrayList<>();
            for (int f = pid * framesPerProc; f < (pid + 1) * framesPerProc; f++) {
                frames[f].assignedToPid = pid;
                frames[f].virtualPage = -1;
                p.assignedFrames.add(f);
            }
        }

        // Cola de procesos que aún tienen referencias
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int pid = 0; pid < nproc; pid++) {
            if (procesos[pid].nextRefIndex < procesos[pid].dvList.size()) queue.add(pid);
        }

        // Simulación por turnos
        while (!queue.isEmpty()) {
            int pid = queue.poll();
            Proceso p = procesos[pid];

            // Intentar procesar la siguiente referencia (si ya terminó no lo reinsertamos)
            if (p.nextRefIndex >= p.dvList.size()) {
                // proceso terminado: reasignar marcos según la política (si aplica)
                reassignFramesWhenProcessEnds(pid);
                continue;
            }

            boolean processed = processNextReference(p);
            // Si el proceso aún tiene referencias después del intento, se reinsertará
            if (p.nextRefIndex < p.dvList.size()) {
                queue.add(pid);
            } else {
                // término: reasignación
                reassignFramesWhenProcessEnds(pid);
            }
        }

        // Al final: imprimir estadísticas por proceso
        System.out.println("----- Estadísticas por proceso -----");
        int totalRefs = 0, totalFaults = 0, totalSwaps = 0, totalHits = 0;
        for (int pid = 0; pid < procesos.length; pid++) {
            Proceso p = procesos[pid];
            totalRefs += p.NR;
            totalFaults += p.faults;
            totalSwaps += p.swaps;
            totalHits += p.hits;
            System.out.printf("Proceso %d: referencias=%d, fallos=%d, accesosSWAP=%d, tasaFallos=%.4f, tasaHits=%.4f%n",
                    pid, p.NR, p.faults, p.swaps,
                    (p.NR==0?0.0: (double)p.faults/p.NR),
                    (p.NR==0?0.0: (double)p.hits/p.NR));
        }
        System.out.println("----- Totales -----");
        System.out.printf("Total referencias=%d, fallos=%d, accesosSWAP=%d, hits=%d%n",
                totalRefs, totalFaults, totalSwaps, totalHits);
    }

    // ---------------------------
    // Procesar una referencia de un proceso: devuelve true si la referencia quedó procesada
    // (es decir: se avanzó nextRefIndex). Si devuelve false significa que hubo fallo y se
    // resolvió (swap), pero la referencia NO fue procesada (demora 1 turno).
    // ---------------------------
    static boolean processNextReference(Proceso p) {
        long dv = p.dvList.get(p.nextRefIndex);
        int page = (int)(dv / p.TP);

        PageTableEntry pte = p.pageTable[page];
        if (pte.loaded) {
            // HIT
            p.hits++;
            pte.lastAccess = globalTime++;
            // avanzar referencia
            p.nextRefIndex++;
            return true;
        } else {
            // FALLA DE PÁGINA
            p.faults++;
            // Resolver la falla: cargar la página (con o sin reemplazo) e incrementar swaps según corresponda.
            boolean usedFreeFrame = false;
            int chosenFrameIdx = -1;
            // Buscar frame libre dentro de los frames asignados al proceso
            for (int fidx : p.assignedFrames) {
                if (frames[fidx].virtualPage == -1) {
                    chosenFrameIdx = fidx;
                    usedFreeFrame = true;
                    break;
                }
            }
            if (usedFreeFrame) {
                // cargar sin reemplazo -> 1 acceso a SWAP
                p.swaps += 1;
                frames[chosenFrameIdx].virtualPage = page;
                pte.loaded = true;
                pte.frameIndex = chosenFrameIdx;
                // marcamos lastAccess con el tiempo actual (aunque la referencia NO se contabiliza aún)
                pte.lastAccess = globalTime++;
            } else {
                // Necesitamos reemplazar una página: LRU entre las páginas del proceso (2 accesos a SWAP)
                p.swaps += 2;
                // seleccionar víctima: la página cargada con menor lastAccess
                int victimPage = -1;
                long minAccess = Long.MAX_VALUE;
                for (int vp = 0; vp < p.NP; vp++) {
                    PageTableEntry e = p.pageTable[vp];
                    if (e.loaded) {
                        if (e.lastAccess < minAccess) {
                            minAccess = e.lastAccess;
                            victimPage = vp;
                        }
                    }
                }
                if (victimPage == -1) {
                    // situación improbable: no páginas cargadas pero tampoco frame libre -> como fallback, tomar primer frame asignado
                    chosenFrameIdx = p.assignedFrames.get(0);
                } else {
                    chosenFrameIdx = p.pageTable[victimPage].frameIndex;
                    // expulsar a victimPage
                    p.pageTable[victimPage].loaded = false;
                    p.pageTable[victimPage].frameIndex = -1;
                }
                // cargar la nueva página
                frames[chosenFrameIdx].virtualPage = page;
                pte.loaded = true;
                pte.frameIndex = chosenFrameIdx;
                pte.lastAccess = globalTime++;
            }

            // Según la especificación, la referencia NO se marca como procesada este turno (demora).
            // Devolvemos false para indicar ese comportamiento: la misma dv será procesada el siguiente turno.
            return false;
        }
    }

    // ---------------------------
    // Reasignar frames cuando un proceso termina:
    // - si hay otros procesos en ejecución, los marcos se reasignan al proceso con mayor #fallos
    // - el contenido de los marcos (páginas) se descarta y quedan libres para el nuevo dueño
    // ---------------------------
    static void reassignFramesWhenProcessEnds(int finishingPid) {
        Proceso fin = procesos[finishingPid];
        // si no tiene frames o ya fueron reasignados, nada que hacer
        if (fin.assignedFrames == null || fin.assignedFrames.isEmpty()) return;

        // Buscar procesos en ejecución (que aún tengan referencias) y escoger el que tenga mayor fallos
        int bestPid = -1;
        int maxFaults = -1;
        for (int pid = 0; pid < procesos.length; pid++) {
            if (pid == finishingPid) continue;
            Proceso p = procesos[pid];
            if (p.nextRefIndex < p.dvList.size()) { // sigue en ejecución
                if (p.faults > maxFaults) {
                    maxFaults = p.faults;
                    bestPid = pid;
                }
            }
        }
        if (bestPid == -1) {
            // no hay procesos en ejecución -> no reasignar
            // marcar frames como sin dueño (opcional). Para seguridad dejarlos con assignedToPid = finishingPid pero virtualPage = -1.
            for (int f : fin.assignedFrames) {
                frames[f].virtualPage = -1;
            }
            fin.assignedFrames.clear();
            return;
        }

        // Reasignar: limpiar contenido de esos frames y pasar assignedToPid al mejor proceso
        Proceso target = procesos[bestPid];
        for (int f : fin.assignedFrames) {
            frames[f].virtualPage = -1;       // contenido descartado (era del proceso que terminó)
            frames[f].assignedToPid = bestPid;
            target.assignedFrames.add(f);
        }
        fin.assignedFrames.clear();
    }

    // ---------------------------
    // Función para cargar un proc<i>.txt
    // Formato esperado: primera línea: TP NF NC NR NP
    // luego NR direcciones (dv) (cada una en su propia línea o separadas por espacios)
    // ---------------------------
    static Proceso loadProcFile(int pid) {
        String filename = "proc" + pid + ".txt";
        File f = new File(filename);
        if (!f.exists()) {
            System.err.println("Archivo no encontrado: " + filename);
            return null;
        }
        try (Scanner sc = new Scanner(f)) {
            Proceso p = new Proceso(pid);
            p.TP = sc.nextInt();
            p.NF = sc.nextInt();
            p.NC = sc.nextInt();
            p.NR = sc.nextInt();
            p.NP = sc.nextInt();
            p.dvList = new ArrayList<>();
            for (int i = 0; i < p.NR; i++) {
                if (!sc.hasNextLong()) {
                    System.err.println("Proc " + pid + ": faltan dv en el archivo (esperado NR=" + p.NR + ").");
                    return null;
                }
                long dv = sc.nextLong();
                p.dvList.add(dv);
            }
            p.pageTable = new PageTableEntry[p.NP];
            for (int i = 0; i < p.NP; i++) p.pageTable[i] = new PageTableEntry();
            return p;
        } catch (Exception ex) {
            System.err.println("Error leyendo " + filename + ": " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }
}
