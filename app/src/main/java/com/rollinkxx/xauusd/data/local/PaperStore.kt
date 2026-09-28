package com.rollinkxx.xauusd.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rollinkxx.xauusd.domain.model.Direction
import com.rollinkxx.xauusd.domain.model.PaperPosition
import com.rollinkxx.xauusd.domain.model.TradeRecord

class PaperStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "paper_account.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE positions(id TEXT PRIMARY KEY, direction TEXT NOT NULL, entry REAL NOT NULL, stop REAL NOT NULL, target REAL NOT NULL, qty REAL NOT NULL, opened INTEGER NOT NULL, market_at INTEGER NOT NULL, score INTEGER NOT NULL, version TEXT NOT NULL, regime TEXT NOT NULL, provider TEXT NOT NULL, signal_id TEXT NOT NULL UNIQUE, spread REAL NOT NULL, slippage REAL NOT NULL, entry_market REAL NOT NULL)")
        db.execSQL("CREATE TABLE trades(id TEXT PRIMARY KEY, direction TEXT NOT NULL, entry REAL NOT NULL, exit REAL NOT NULL, qty REAL NOT NULL, opened INTEGER NOT NULL, closed INTEGER NOT NULL, gross REAL NOT NULL, costs REAL NOT NULL, net REAL NOT NULL, reason TEXT NOT NULL, score INTEGER NOT NULL, version TEXT NOT NULL, regime TEXT NOT NULL, provider TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE positions ADD COLUMN entry_market REAL NOT NULL DEFAULT 0")
    }

    fun positions(): List<PaperPosition> = readableDatabase.rawQuery("SELECT * FROM positions ORDER BY opened DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(PaperPosition(c.getString(0), Direction.valueOf(c.getString(1)), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5), c.getLong(6), c.getLong(7), c.getInt(8), c.getString(9), c.getString(10), c.getString(11), c.getString(12), c.getDouble(13), c.getDouble(14), c.getDouble(15).takeIf { it > 0.0 })) }
    }

    fun history(): List<TradeRecord> = readableDatabase.rawQuery("SELECT * FROM trades ORDER BY closed DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(TradeRecord(c.getString(0), Direction.valueOf(c.getString(1)), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getLong(5), c.getLong(6), c.getDouble(7), c.getDouble(8), c.getDouble(9), c.getString(10), c.getInt(11), c.getString(12), c.getString(13), c.getString(14))) }
    }

    fun insert(position: PaperPosition) {
        val v = ContentValues().apply {
            put("id", position.id); put("direction", position.direction.name); put("entry", position.entryPrice)
            put("stop", position.stopLoss); put("target", position.takeProfit); put("qty", position.quantityOz)
            put("opened", position.openedAt); put("market_at", position.marketDataAt); put("score", position.signalScore)
            put("version", position.strategyVersion); put("regime", position.regime); put("provider", position.provider)
            put("signal_id", position.signalId); put("spread", position.spreadAssumption); put("slippage", position.slippageAssumption)
            put("entry_market", position.entryMarketPrice ?: position.entryPrice)
        }
        check(writableDatabase.insertOrThrow("positions", null, v) != -1L)
    }

    fun close(trade: TradeRecord) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("positions", "id = ?", arrayOf(trade.id))
            val v = ContentValues().apply {
                put("id", trade.id); put("direction", trade.direction.name); put("entry", trade.entryPrice); put("exit", trade.exitPrice)
                put("qty", trade.quantityOz); put("opened", trade.openedAt); put("closed", trade.closedAt)
                put("gross", trade.grossPnl); put("costs", trade.costs); put("net", trade.netPnl); put("reason", trade.exitReason)
                put("score", trade.signalScore); put("version", trade.strategyVersion); put("regime", trade.regime); put("provider", trade.provider)
            }
            db.insertOrThrow("trades", null, v)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun reset() { writableDatabase.beginTransaction(); try { writableDatabase.delete("positions", null, null); writableDatabase.delete("trades", null, null); writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() } }
}
