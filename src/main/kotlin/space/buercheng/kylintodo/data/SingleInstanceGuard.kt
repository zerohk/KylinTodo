package space.buercheng.kylintodo.data

import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 单实例守卫。
 *
 * ## 解决的问题
 * 用户多次点击图标会打开多个应用窗口。原因不是"点击太快"，
 * 而是应用**没有任何互斥机制** —— 每次启动都是一个独立进程，
 * 各自打开自己的窗口、各自连一个 SQLite 连接。
 *
 * 多实例不只碍眼，还有数据风险：两个进程同时写同一个 SQLite 文件，
 * 可能出现写冲突或"在 A 窗口加的待办在 B 窗口看不到"。
 *
 * ## 实现方式
 * 用**文件锁**（[FileLock]）而非"检测进程名"或"查找窗口标题"：
 *  - 文件锁由操作系统在进程退出时（包括崩溃、被 kill）自动释放，
 *    不会留下需要人工清理的陈旧状态；用 PID 文件则必须处理
 *    "进程已死但文件还在"的情况
 *  - 跨平台语义一致（Windows 与 Linux 都支持）
 *
 * 锁文件放在应用数据目录，与其他状态文件集中管理。
 */
class SingleInstanceGuard private constructor(
    private val file: RandomAccessFile,
    private val lock: FileLock,
) {

    /** 释放锁。进程退出时操作系统也会释放，但显式调用更干净。 */
    fun release() {
        runCatching { lock.release() }
        runCatching { file.close() }
    }

    companion object {

        private const val LOCK_FILE_NAME = "app.lock"

        /**
         * 尝试取得独占锁。
         *
         * @return 取得成功返回守卫对象；**已有实例在运行**时返回 null。
         */
        fun tryAcquire(): SingleInstanceGuard? {
            val lockFile: Path = AppPaths.dataDirectory().resolve(LOCK_FILE_NAME)
            return runCatching {
                Files.createDirectories(lockFile.parent)
                val raf = RandomAccessFile(lockFile.toFile(), "rw")
                val channel = raf.channel
                val fileLock = try {
                    channel.tryLock()
                } catch (e: OverlappingFileLockException) {
                    // 同一 JVM 内已持有 —— 对"多实例"来说同样应视为已有实例
                    null
                }
                if (fileLock == null) {
                    runCatching { raf.close() }
                    null
                } else {
                    SingleInstanceGuard(raf, fileLock)
                }
            }.getOrNull()
        }
    }
}
