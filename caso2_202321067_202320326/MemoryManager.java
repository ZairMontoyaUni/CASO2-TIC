// File: MemoryManager.java
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class MemoryManager {
    private static class Frame {
        int ownerPid;   // pid al que está asignado este frame (o -1 si libre para asignación)
        int page;       // página cargada (-1 si vacío)
        long lastAccess;// timestamp para LRU
        boolean occupied;
        Frame() { ownerPid = -1; page = -1; lastAccess = 0; occupied = false; }
    }

    private final Frame[] frames;
    private final AtomicLong globalTime;

    public MemoryManager(int totalFrames, AtomicLong globalTime) {
        this.frames = new Frame[totalFrames];
        for (int i = 0; i < totalFrames; i++) frames[i] = new Frame();
        this.globalTime = globalTime;
    }

    // Asigna inicialmente 'count' frames al proceso pid (marca ownerPid)
    public void assignInitialFrames(int pid, int count) {
        int assigned = 0;
        for (int i = 0; i < frames.length && assigned < count; i++) {
            if (frames[i].ownerPid == -1) {
                frames[i].ownerPid = pid;
                frames[i].page = -1;
                frames[i].occupied = false;
                assigned++;
            }
        }
        // si no se asignaron suficientes, los restantes quedarán sin asignar (se podrían repartir luego)
    }

    // Retorna true si la página ya está disponible (hit), false si hubo fallo y se cargó (o reemplazó)
    // NOTA: en este diseño, en caso de fallo se carga la página y devolvemos false para modelar
    // la demora de 1 turno. La siguiente llamada será un hit.
    public boolean accessPage(int pid, int page, boolean isWrite, Proceso p, AtomicLong extGlobalTime) {
        // 1) ¿la página ya está mapeada en el proceso?
        int frameId = p.frameOfPage(page);
        if (frameId != -1) {
            // hit
            frames[frameId].lastAccess = extGlobalTime.incrementAndGet();
            p.recordHit();
            return true;
        }

        // 2) fallo de página
        p.recordFault();

        // 2.a) buscar frame libre asignado a pid
        Integer freeFrame = null;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i].ownerPid == pid && !frames[i].occupied) {
                freeFrame = i;
                break;
            }
        }

        if (freeFrame != null) {
            // cargar página en freeFrame (1 acceso swap según especificación)
            frames[freeFrame].page = page;
            frames[freeFrame].occupied = true;
            frames[freeFrame].lastAccess = extGlobalTime.incrementAndGet();
            p.mapPageToFrame(page, freeFrame);
            p.recordSwap(1);
            // devolvemos false para indicar que se demoró (simulador reintentará después)
            return false;
        }

        // 2.b) no hay frames libres asignados a pid -> reemplazar LRU entre frames del pid
        long oldest = Long.MAX_VALUE; int victim = -1;
        for (int i = 0; i < frames.length; i++) {
            if (frames[i].ownerPid == pid) {
                if (frames[i].lastAccess < oldest) {
                    oldest = frames[i].lastAccess;
                    victim = i;
                }
            }
        }

        if (victim == -1) {
            // caso extremo: no hay frames asignados al pid. Tomamos cualquier frame y lo asignamos (poco probable)
            for (int i = 0; i < frames.length; i++) {
                if (frames[i].ownerPid == -1) { victim = i; break; }
            }
            if (victim == -1) victim = 0;
        }

        // reemplazo: desapuntar la página del proceso (si hubiera)
        int oldPage = frames[victim].page;
        if (oldPage != -1) {
            // Si la tabla de página del mismo proceso tenía ese mapeo, hay que invalidarlo.
            // Como en este diseño los frames pertenecen al mismo pid, usamos p.unmapPage(oldPage).
            // (si fuera otro proceso, necesitaríamos referencia a ese Proceso para desmapearlo)
            p.unmapPage(oldPage);
        }

        // cargar nueva página (reemplazo -> 2 accesos a swap según especificación)
        frames[victim].page = page;
        frames[victim].occupied = true;
        frames[victim].lastAccess = extGlobalTime.incrementAndGet();
        p.mapPageToFrame(page, victim);
        p.recordSwap(2);

        // devolvemos false para indicar que hubo fallo y se demoró 1 turno
        return false;
    }

    // Cuando un proceso termina, reasignar sus marcos al proceso con mayor número de fallos (si existe)
    public void reassignFramesAfterProcessEnds(int pidEnded, List<Proceso> procesos) {
        // buscar proceso candidato (mayor pageFaults, distinto de pidEnded)
        int bestPid = -1; int maxFaults = -1;
        for (Proceso pr : procesos) {
            if (pr.id == pidEnded) continue;
            if (pr.getPageFaults() > maxFaults) {
                maxFaults = pr.getPageFaults();
                bestPid = pr.id;
            }
        }
        for (Frame f : frames) {
            if (f.ownerPid == pidEnded) {
                f.page = -1;
                f.occupied = false;
                f.lastAccess = 0;
                f.ownerPid = (bestPid == -1 ? -1 : bestPid);
            }
        }
    }
}
