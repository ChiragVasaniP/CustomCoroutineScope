import android.os.Looper
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class CustomCoroutineScope : CoroutineScope {

    private var job: Job = Job() // Job to manage all coroutines in this scope
    private var dispatcher: CoroutineDispatcher = Dispatchers.Default // Default dispatcher

    private var customThread: Thread? = null // Custom thread for the scope
    private var customHandler: CoroutineExceptionHandler? = null // Custom exception handler

    private val mainDispatcher = Dispatchers.Main // Main dispatcher for Android UI operations

    private val memoryThresholdMb = 100 // Example memory threshold in MB

    /**
     * Constructor to initialize the CustomCoroutineScope.
     * Creates a new custom thread if necessary.
     */
    constructor() {
        createCustomThreadIfNeeded()
    }

    /**
     * Coroutine context for this scope.
     * Includes the custom dispatcher and exception handler.
     */
    override val coroutineContext: CoroutineContext
        get() = dispatcher + job

    /**
     * Launches a coroutine in this scope with error handling and completion callback.
     */
    fun launchCustom(
        block: suspend CoroutineScope.() -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ): Job {
        return launch {
            try {
                block()
                onComplete?.invoke()
            } catch (e: Throwable) {
                onError?.invoke(e)
            }
        }
    }

    /**
     * Cancels all coroutines in this scope.
     * Interrupts the custom thread if it exists.
     */
    fun cancel() {
        job.cancel() // Cancel all coroutines
        customThread?.interrupt() // Interrupt the custom thread
    }

    /**
     * Checks if this scope is active (has active jobs) and if the custom thread is alive.
     */
    fun isActive(): Boolean {
        return job.isActive && customThread?.isAlive == true
    }

    /**
     * Creates a new custom thread if no thread exists or the existing one is not alive.
     * Uses Looper to manage messages and a CoroutineExceptionHandler for error handling.
     */
    private fun createCustomThreadIfNeeded() {
        if (customThread == null || !customThread!!.isAlive) {
            customThread = Thread {
                Looper.prepare()
                customHandler = CoroutineExceptionHandler { _, throwable ->
                    // Handle exceptions here (e.g., log, report, etc.)
                    println("Coroutine Exception on Custom Thread: $throwable")
                }
                dispatcher = customHandler!! + Dispatchers.Default // Use Dispatchers.Default or other as needed
                Looper.loop()
            }
            customThread!!.start()
        }
    }

    /**
     * Determines if a new custom thread should be created based on a memory usage threshold.
     * Adjust the condition as per your specific memory management needs.
     */
    private fun shouldCreateNewThread(): Boolean {
        // Example condition based on memory usage
        val memoryInfo = Runtime.getRuntime().freeMemory() / (1024 * 1024) // Convert to MB
        return memoryInfo < memoryThresholdMb
    }

    /**
     * Launches a coroutine with automatic thread management.
     * Creates a new custom thread if needed before executing the coroutine block.
     * Ensures onComplete and onError callbacks run on the main thread.
     */
    fun launchAutoManage(
        block: suspend CoroutineScope.() -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ): Job {
        return launch {
            if (shouldCreateNewThread()) {
                createCustomThreadIfNeeded()
            }
            try {
                block()
                // Ensure UI updates are on the main thread
                withContext(mainDispatcher) {
                    onComplete?.invoke()
                }
            } catch (e: Throwable) {
                // Handle exceptions on the main thread
                withContext(mainDispatcher) {
                    onError?.invoke(e)
                }
            }
        }
    }
}
