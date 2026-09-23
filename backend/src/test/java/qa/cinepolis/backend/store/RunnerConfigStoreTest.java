package qa.cinepolis.backend.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TAREA — hacer opcional la captura de tráfico vía Dashboard.
 *
 * Pruebas puras (sin Spring, sin base de datos — RunnerConfigStore es un
 * singleton en memoria, mismo patrón ya usado por repositoryUrl/branch/appPackage
 * en esta misma clase) del toggle networkMonitoringEnabled.
 */
@DisplayName("RunnerConfigStore — toggle networkMonitoringEnabled")
class RunnerConfigStoreTest {

    @Test
    @DisplayName("1. Valor por defecto (sin env var, instalación nueva o existente) -> false")
    void defaultIsFalse() {
        RunnerConfigStore store = new RunnerConfigStore();
        assertFalse(store.isNetworkMonitoringEnabled());
    }

    @Test
    @DisplayName("2. setNetworkMonitoringEnabled(true) persiste en memoria hasta el próximo cambio")
    void setTrue_persistsUntilChanged() {
        RunnerConfigStore store = new RunnerConfigStore();
        store.setNetworkMonitoringEnabled(true);
        assertTrue(store.isNetworkMonitoringEnabled());
    }

    @Test
    @DisplayName("3. setNetworkMonitoringEnabled(false) tras haber estado en true -> vuelve a false")
    void setFalse_afterTrue_persists() {
        RunnerConfigStore store = new RunnerConfigStore();
        store.setNetworkMonitoringEnabled(true);
        store.setNetworkMonitoringEnabled(false);
        assertFalse(store.isNetworkMonitoringEnabled());
    }

    @Test
    @DisplayName("4. El resto de la configuración (repo/branch/appPackage) no se ve afectada "
            + "por el toggle de red")
    void networkToggle_doesNotAffectOtherConfig() {
        RunnerConfigStore store = new RunnerConfigStore();
        String repoAntes = store.getRepositoryUrl();
        String branchAntes = store.getBranch();

        store.setNetworkMonitoringEnabled(true);

        assertEquals(repoAntes, store.getRepositoryUrl());
        assertEquals(branchAntes, store.getBranch());
    }
}
