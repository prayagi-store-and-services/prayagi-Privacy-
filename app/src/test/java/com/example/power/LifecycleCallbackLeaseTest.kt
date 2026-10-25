package com.example.power

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LifecycleCallbackLeaseTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    @Test fun stopUnregistersAndRestartRegistersOnlyOnce() {
        val owner = Owner(); var starts = 0; var stops = 0; var visible = false
        val lease = LifecycleCallbackLease(owner.lifecycle, { starts++ }, { stops++ }, { visible = it })
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        assertEquals(0, starts)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(1, starts); assertTrue(visible)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(0, stops)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertEquals(1, stops); assertFalse(visible)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(2, starts)
        lease.close(); assertEquals(2, stops); assertFalse(visible)
        lease.close(); assertEquals(2, stops)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(2, starts)
    }
    @Test fun disposeWhileStartedUnregistersAndDoesNotRecover() {
        val owner = Owner(); owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        var starts = 0; var stops = 0
        val lease = LifecycleCallbackLease(owner.lifecycle, { starts++ }, { stops++ }, {})
        assertEquals(1, starts)
        lease.close(); assertEquals(1, stops)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(1, starts); assertEquals(1, stops)
    }
    @Test fun disposeWhileStoppedNeverRegisters() {
        val owner = Owner(); var starts = 0; var stops = 0
        val lease = LifecycleCallbackLease(owner.lifecycle, { starts++ }, { stops++ }, {})
        lease.close()
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(0, starts); assertEquals(0, stops)
    }
}
