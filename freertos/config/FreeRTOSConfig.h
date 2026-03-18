/*
 * FreeRTOS Configuration for the CalBot Calculator
 *
 * This configures an embedded real-time operating system — designed for
 * microcontrollers with 4KB of RAM — to run inside a shared library,
 * loaded via JNI, on an Android phone with 8GB of RAM, to schedule
 * arithmetic tasks for a calculator app.
 *
 * The RTOS tick rate is 1000Hz. The task priorities range from 1 (idle,
 * addition) to 6 (critical, division — because dividing by zero is a
 * safety-critical operation that demands real-time guarantees).
 */

#ifndef FREERTOS_CONFIG_H
#define FREERTOS_CONFIG_H

/* Core scheduler settings */
#define configUSE_PREEMPTION                    1
#define configUSE_PORT_OPTIMISED_TASK_SELECTION 0
#define configTICK_RATE_HZ                      ( 1000 )
#define configUSE_TIME_SLICING                  1

/* Task settings */
#define configMINIMAL_STACK_SIZE                ( ( unsigned short ) 1024 )
#define configMAX_TASK_NAME_LEN                 ( 16 )
#define configMAX_PRIORITIES                    ( 7 )

/* Memory — 64KB of RTOS heap. On a phone with 8GB. */
#define configTOTAL_HEAP_SIZE                   ( ( size_t ) ( 64 * 1024 ) )
#define configSUPPORT_STATIC_ALLOCATION         1
#define configSUPPORT_DYNAMIC_ALLOCATION        1
#define configSTACK_DEPTH_TYPE                  uint32_t

/* Feature toggles */
#define configUSE_MUTEXES                       1
#define configUSE_RECURSIVE_MUTEXES             1
#define configUSE_COUNTING_SEMAPHORES           1
#define configUSE_QUEUE_SETS                    1
#define configQUEUE_REGISTRY_SIZE               8
#define configUSE_IDLE_HOOK                     0
#define configUSE_TICK_HOOK                     0
#define configUSE_DAEMON_TASK_STARTUP_HOOK      0
#define configUSE_TRACE_FACILITY                0
#define configUSE_16_BIT_TICKS                  0
#define configIDLE_SHOULD_YIELD                 1
#define configUSE_ALTERNATIVE_API               0
#define configUSE_NEWLIB_REENTRANT              0
#define configENABLE_BACKWARD_COMPATIBILITY     0
#define configNUM_THREAD_LOCAL_STORAGE_POINTERS 0
#define configUSE_TIMERS                        0
#define configUSE_APPLICATION_TASK_TAG          0
#define configRECORD_STACK_HIGH_ADDRESS         1

/* INCLUDE settings */
#define INCLUDE_vTaskPrioritySet                1
#define INCLUDE_uxTaskPriorityGet               1
#define INCLUDE_vTaskDelete                     1
#define INCLUDE_vTaskSuspend                    1
#define INCLUDE_vTaskDelayUntil                 1
#define INCLUDE_vTaskDelay                      1
#define INCLUDE_xTaskGetSchedulerState          1
#define INCLUDE_xTaskGetCurrentTaskHandle       1
#define INCLUDE_xTimerPendFunctionCall          0
#define INCLUDE_xTaskAbortDelay                 0
#define INCLUDE_xTaskGetHandle                  0
#define INCLUDE_xSemaphoreGetMutexHolder        0

/* Assert handler */
void vAssertCalled(const char *file, int line);
#define configASSERT( x ) if( ( x ) == 0 ) vAssertCalled( __FILE__, __LINE__ )

#endif /* FREERTOS_CONFIG_H */
