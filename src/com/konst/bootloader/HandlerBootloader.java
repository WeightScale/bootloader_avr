package com.konst.bootloader;

import android.os.Handler;

/** Обработчик сообщений программатора.
 *  
 */
public class HandlerBootloader extends Handler {

    /**
     * Энумератор сообщений программатора.
     */
    public enum Result{
        /** Сообщение для логов */
        MSG_LOG,
        /** Закрытие прогресс диалога */
        MSG_CLOSE_DIALOG,
        /** Обновление прогресс диалога */
        MSG_UPDATE_DIALOG,
        /** Открытие прогресс диалога */
        MSG_SHOW_DIALOG
    }

}
