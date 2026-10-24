package com.bte.service;
import com.bte.controller.SimulatedPaymentController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
class SimulationProfileTest {
    ApplicationContextRunner context(String profile, boolean enabled) {
        return new ApplicationContextRunner().withInitializer(c -> c.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("app.payments.simulation-enabled=" + enabled)
                .withBean(VoucherService.class, () -> mock(VoucherService.class))
                .withUserConfiguration(SimulatedPaymentController.class);
    }
    @Test void productionNeverExposesSimulatedPaymentsEvenWhenFlagIsTrue() { context("prod", true).run(c -> assertThat(c).doesNotHaveBean(SimulatedPaymentController.class)); }
    @Test void developmentCanDisableSimulation() { context("dev", false).run(c -> assertThat(c).doesNotHaveBean(SimulatedPaymentController.class)); }
    @Test void enabledDevelopmentExposesSimulation() { context("dev", true).run(c -> assertThat(c).hasSingleBean(SimulatedPaymentController.class)); }
}
