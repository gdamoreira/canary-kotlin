package org.opentibia.canary.lib.di

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LibInjectorTest {

    interface Service

    class ServiceImpl : Service

    class Consumer(val service: Service)

    @Test
    fun `resolves a registered binding by its expected type`() {
        val injector = Injector().bind(Service::class) { ServiceImpl() }

        assertThat(injector.get(Service::class)).isInstanceOf(ServiceImpl::class.java)
        assertThat(injector.isBound(Service::class)).isTrue()
        assertThat(injector.isBound(Consumer::class)).isFalse()
    }

    @Test
    fun `create always builds a new instance`() {
        val injector = Injector().bind(Service::class) { ServiceImpl() }

        assertThat(injector.create(Service::class)).isNotSameAs(injector.create(Service::class))
    }

    @Test
    fun `get reuses the shared instance`() {
        val injector = Injector().bind(Service::class) { ServiceImpl() }

        assertThat(injector.get(Service::class)).isSameAs(injector.get(Service::class))
    }

    @Test
    fun `binding factories can resolve dependencies from the injector`() {
        val injector = Injector()
            .bind(Service::class) { ServiceImpl() }
            .bind(Consumer::class) { it.get(Service::class).let(::Consumer) }

        assertThat(injector.get(Consumer::class).service).isSameAs(injector.get(Service::class))
    }

    @Test
    fun `constructor injection resolves unbound concrete types`() {
        val injector = Injector().bind(Service::class) { ServiceImpl() }

        val consumer = injector.get(Consumer::class)

        assertThat(consumer.service).isSameAs(injector.get(Service::class))
    }

    @Test
    fun `installs the bindings of another injector`() {
        val source = Injector().bind(Service::class) { ServiceImpl() }

        val target = Injector().install(source)

        assertThat(target.get(Service::class)).isInstanceOf(ServiceImpl::class.java)
    }

    @Test
    fun `unbound abstract types fail with the assert error policy message`() {
        assertThatThrownBy { Injector().get(Service::class) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Type not bound!")
    }
}
