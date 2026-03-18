/**
 * FreeRTOS Calculator Task Scheduler
 *
 * An embedded real-time operating system, designed for microcontrollers
 * running on 4KB of RAM, is being used here to schedule arithmetic
 * operations on an Android phone.
 *
 * Each arithmetic operation is a FreeRTOS task with a different priority:
 *   - Addition:       Priority 2 (low)     — adding is easy
 *   - Subtraction:    Priority 3 (normal)  — slightly harder
 *   - Multiplication: Priority 4 (high)    — O(n) if you think about it
 *   - Division:       Priority 6 (critical)— div/0 is safety-critical
 *
 * Tasks communicate via shared memory + atomics. The FreeRTOS scheduler
 * preemptively switches between them using POSIX signals on pthreads.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <pthread.h>
#include <unistd.h>

#include "FreeRTOS.h"
#include "task.h"
#include "queue.h"
#include "semphr.h"

/* ------------------------------------------------------------------ */
/* Shared state — results written by tasks, read after scheduler stops */
/* ------------------------------------------------------------------ */

static volatile int32_t g_results[3];
static volatile int32_t g_result_count = 0;
static volatile int32_t g_req_a;
static volatile int32_t g_req_b;
static volatile char    g_req_op;
static pthread_t g_scheduler_thread;

/* ------------------------------------------------------------------ */
/* FreeRTOS required callbacks                                         */
/* ------------------------------------------------------------------ */

void vAssertCalled(const char *file, int line) {
    fprintf(stderr, "[FreeRTOS ASSERT] %s:%d — the RTOS running inside "
                    "your calculator just crashed.\n", file, line);
    /* Don't abort — let the caller handle the failure */
}

void vApplicationGetIdleTaskMemory(StaticTask_t **ppxIdleTaskTCBBuffer,
                                    StackType_t **ppxIdleTaskStackBuffer,
                                    configSTACK_DEPTH_TYPE *pulIdleTaskStackSize) {
    static StaticTask_t idle_tcb;
    static StackType_t  idle_stack[configMINIMAL_STACK_SIZE];
    *ppxIdleTaskTCBBuffer   = &idle_tcb;
    *ppxIdleTaskStackBuffer = idle_stack;
    *pulIdleTaskStackSize   = configMINIMAL_STACK_SIZE;
}

/* ------------------------------------------------------------------ */
/* Arithmetic                                                          */
/* ------------------------------------------------------------------ */

static int32_t do_calc(int32_t a, int32_t b, char op) {
    switch (op) {
        case '+': return a + b;
        case '-': return a - b;
        case '*': return a * b;
        case '/': return (b == 0) ? INT32_MIN : a / b;
        default:  return INT32_MIN;
    }
}

static UBaseType_t priority_for_op(char op) {
    switch (op) {
        case '+': return 2;  /* Low — addition is trivial */
        case '-': return 3;  /* Normal */
        case '*': return 4;  /* High */
        case '/': return 6;  /* CRITICAL — division by zero is safety-critical */
        default:  return 1;
    }
}

/* ------------------------------------------------------------------ */
/* Worker task — reads params from globals, writes result atomically   */
/* ------------------------------------------------------------------ */

static void vWorkerTask(void *pvParameters) {
    int32_t my_id = (int32_t)(intptr_t)pvParameters;

    /* Waste some ticks so the RTOS scheduler actually has work to do */
    vTaskDelay(pdMS_TO_TICKS(5 + my_id * 3));

    int32_t result = do_calc(g_req_a, g_req_b, g_req_op);

    /* Store result via atomics — no queue needed, no yield outside RTOS */
    int32_t idx = __atomic_fetch_add(&g_result_count, 1, __ATOMIC_SEQ_CST);
    if (idx < 3) {
        g_results[idx] = result;
    }

    vTaskDelete(NULL);
}

/* ------------------------------------------------------------------ */
/* Bootstrap task — creates workers then waits for completion          */
/* ------------------------------------------------------------------ */

static void vBootstrapTask(void *pvParameters) {
    (void)pvParameters;

    UBaseType_t prio = priority_for_op(g_req_op);
    char name[16];

    for (int i = 0; i < 3; i++) {
        snprintf(name, sizeof(name), "Calc_%c_%d", g_req_op, i);
        xTaskCreate(vWorkerTask, name, configMINIMAL_STACK_SIZE,
                    (void *)(intptr_t)i, prio, NULL);
    }

    /* Wait for all 3 workers to finish */
    while (__atomic_load_n(&g_result_count, __ATOMIC_SEQ_CST) < 3) {
        vTaskDelay(pdMS_TO_TICKS(5));
    }

    /* Let workers fully exit */
    vTaskDelay(pdMS_TO_TICKS(30));

    vTaskEndScheduler();
    for (;;) { vTaskDelay(pdMS_TO_TICKS(1000)); }
}

/* ------------------------------------------------------------------ */
/* Scheduler thread                                                    */
/* ------------------------------------------------------------------ */

static void *scheduler_thread_fn(void *arg) {
    (void)arg;
    vTaskStartScheduler();
    return NULL;
}

/* ------------------------------------------------------------------ */
/* Public API                                                          */
/* ------------------------------------------------------------------ */

int32_t rtos_compute(int32_t a, int32_t b, char op) {
    g_req_a = a;
    g_req_b = b;
    g_req_op = op;
    g_result_count = 0;
    g_results[0] = g_results[1] = g_results[2] = INT32_MIN;

    xTaskCreate(vBootstrapTask, "Bootstrap", configMINIMAL_STACK_SIZE,
                NULL, configMAX_PRIORITIES - 1, NULL);

    pthread_create(&g_scheduler_thread, NULL, scheduler_thread_fn, NULL);

    /* Spin from the non-RTOS side */
    for (int i = 0; i < 5000; i++) {
        if (__atomic_load_n(&g_result_count, __ATOMIC_SEQ_CST) >= 3) break;
        usleep(1000);
    }

    pthread_join(g_scheduler_thread, NULL);

    int32_t count = __atomic_load_n(&g_result_count, __ATOMIC_SEQ_CST);
    if (count == 0) return INT32_MIN;

    /* Triple modular redundancy vote */
    if (count >= 3 && g_results[0] == g_results[1]) return g_results[0];
    if (count >= 3 && g_results[0] == g_results[2]) return g_results[0];
    if (count >= 3 && g_results[1] == g_results[2]) return g_results[1];
    return g_results[0];
}

/* ------------------------------------------------------------------ */
/* JNI export                                                          */
/* ------------------------------------------------------------------ */

#include <jni.h>

JNIEXPORT jint JNICALL
Java_edu_singaporetech_inf2007quiz01_FreeRtosBridge_nativeCompute(
    JNIEnv *env, jobject thiz, jint a, jint b, jbyte op)
{
    (void)env;
    (void)thiz;
    return (jint)rtos_compute((int32_t)a, (int32_t)b, (char)op);
}
