package com.estatedesk.crm.data

import android.database.sqlite.SQLiteDatabase
import java.util.Calendar

/** Aggregations for the dashboard and analytics screens. All numbers come
 *  from real stored data — nothing is simulated. */
class StatsDao(private val db: SQLiteDatabase) {

    fun scalar(sql: String, vararg args: String): Long {
        db.rawQuery(sql, args).use { c -> return if (c.moveToFirst()) c.getLong(0) else 0 }
    }

    fun totalLeads(): Long = scalar("SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL")
    fun qualifiedLeads(): Long =
        scalar("SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL AND stage IN ('Qualified','Requirement Collected','Property Matched','Viewing Scheduled','Viewing Completed','Negotiation','Documentation','Closed Won')")

    fun activeListings(): Long =
        scalar("SELECT COUNT(*) FROM properties WHERE deleted_at IS NULL AND status IN ('Available','Reserved','Pending')")

    fun activeDeals(): Long =
        scalar("SELECT COUNT(*) FROM deals WHERE deleted_at IS NULL AND stage NOT IN ('Closed Won','Closed Lost')")

    fun closedWon(): Long =
        scalar("SELECT COUNT(*) FROM deals WHERE deleted_at IS NULL AND stage='Closed Won'")

    fun closedLost(): Long =
        scalar("SELECT COUNT(*) FROM deals WHERE deleted_at IS NULL AND stage='Closed Lost'")

    fun totalRevenue(): Long =
        scalar("SELECT COALESCE(SUM(value),0) FROM deals WHERE deleted_at IS NULL AND stage='Closed Won'")

    fun totalCommission(): Long =
        scalar("SELECT COALESCE(SUM(commission_amount),0) FROM deals WHERE deleted_at IS NULL AND stage='Closed Won'")

    fun pendingCommission(): Long =
        scalar("SELECT COALESCE(SUM(commission_amount - commission_received),0) FROM deals WHERE deleted_at IS NULL AND stage='Closed Won'")

    fun receivedCommission(): Long =
        scalar("SELECT COALESCE(SUM(commission_received),0) FROM deals WHERE deleted_at IS NULL")

    fun conversionRate(): Int {
        val closed = closedWon().toDouble()
        val total = totalLeads().toDouble()
        if (total <= 0) return 0
        return (closed / total * 100).toInt()
    }

    // ------------------------------------------------------------ today

    fun dayRange(daysAgo: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    fun followUpsDueToday(start: Long, end: Long): Long =
        scalar("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='Open' AND kind='Follow-up' AND due_date>=? AND due_date<?",
            start.toString(), end.toString())

    fun overdueFollowUps(start: Long): Long =
        scalar("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='Open' AND kind='Follow-up' AND due_date>0 AND due_date<?",
            start.toString())

    fun tasksDueToday(start: Long, end: Long): Long =
        scalar("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='Open' AND due_date>=? AND due_date<?",
            start.toString(), end.toString())

    fun meetingsToday(start: Long, end: Long): Long =
        scalar("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='Open' AND kind='Meeting' AND due_date>=? AND due_date<?",
            start.toString(), end.toString())

    fun viewingsToday(start: Long, end: Long): Long =
        scalar("SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND status='Open' AND kind='Viewing' AND due_date>=? AND due_date<?",
            start.toString(), end.toString())

    fun newLeadsSince(since: Long): Long =
        scalar("SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL AND created_at>=?", since.toString())

    fun newContactsSince(since: Long): Long =
        scalar("SELECT COUNT(*) FROM contacts WHERE deleted_at IS NULL AND created_at>=?", since.toString())

    // ------------------------------------------------------------ series

    fun leadsByDay(since: Long, buckets: Int): List<Pair<Long, Long>> {
        val dayMs = 86_400_000L
        val out = mutableListOf<Pair<Long, Long>>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        for (i in buckets - 1 downTo 0) {
            val start = today - i * dayMs
            val count = scalar(
                "SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL AND created_at>=? AND created_at<?",
                start.toString(), (start + dayMs).toString()
            )
            out.add(start to count)
        }
        return out
    }

    fun leadsByMonth(since: Long, buckets: Int): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        for (i in buckets - 1 downTo 0) {
            val startCal = cal.clone() as Calendar
            startCal.add(Calendar.MONTH, -i)
            val start = startCal.timeInMillis
            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.MONTH, 1)
            val count = scalar(
                "SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL AND created_at>=? AND created_at<?",
                start.toString(), endCal.timeInMillis.toString()
            )
            out.add(start to count)
        }
        return out
    }

    fun sources(since: Long): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT source, COUNT(*) FROM leads WHERE deleted_at IS NULL AND created_at>=? AND source!='' GROUP BY source ORDER BY COUNT(*) DESC",
            arrayOf(since.toString())
        ).use { c ->
            val out = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
            return out
        }
    }

    fun pipelineDistribution(): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT stage, COUNT(*) FROM leads WHERE deleted_at IS NULL GROUP BY stage ORDER BY COUNT(*) DESC",
            null
        ).use { c ->
            val out = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
            return out
        }
    }

    /** Lead → Qualified → Viewing → Negotiation → Closed Won funnel. */
    fun funnel(): List<Pair<String, Long>> {
        val stages = listOf(
            "Leads" to "1=1",
            "Qualified" to "stage IN ('Qualified','Requirement Collected','Property Matched','Viewing Scheduled','Viewing Completed','Negotiation','Documentation','Closed Won')",
            "Viewing" to "stage IN ('Viewing Scheduled','Viewing Completed','Negotiation','Documentation','Closed Won')",
            "Negotiation" to "stage IN ('Negotiation','Documentation','Closed Won')",
            "Closed Won" to "stage='Closed Won'"
        )
        return stages.map { (label, where) ->
            label to scalar("SELECT COUNT(*) FROM leads WHERE deleted_at IS NULL AND $where")
        }
    }

    fun revenueByMonth(buckets: Int): List<Pair<Long, Long>> {
        val out = mutableListOf<Pair<Long, Long>>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        for (i in buckets - 1 downTo 0) {
            val startCal = cal.clone() as Calendar
            startCal.add(Calendar.MONTH, -i)
            val start = startCal.timeInMillis
            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.MONTH, 1)
            val sum = scalar(
                "SELECT COALESCE(SUM(value),0) FROM deals WHERE deleted_at IS NULL AND stage='Closed Won' AND actual_close>=? AND actual_close<?",
                start.toString(), endCal.timeInMillis.toString()
            )
            out.add(start to sum)
        }
        return out
    }

    fun propertyTypes(): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT type, COUNT(*) FROM properties WHERE deleted_at IS NULL AND type!='' GROUP BY type ORDER BY COUNT(*) DESC",
            null
        ).use { c ->
            val out = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
            return out
        }
    }

    fun topAreas(limit: Int): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT COALESCE(NULLIF(area_name,''), location), COUNT(*) FROM properties WHERE deleted_at IS NULL GROUP BY 1 ORDER BY COUNT(*) DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            val out = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
            return out
        }
    }

    fun activityCounts(since: Long): Map<String, Long> {
        db.rawQuery(
            "SELECT type, COUNT(*) FROM activities WHERE at>=? GROUP BY type",
            arrayOf(since.toString())
        ).use { c ->
            val out = mutableMapOf<String, Long>()
            while (c.moveToNext()) out[c.getString(0)] = c.getLong(1)
            return out
        }
    }

    fun listingStatusCounts(): List<Pair<String, Long>> {
        db.rawQuery(
            "SELECT status, COUNT(*) FROM properties WHERE deleted_at IS NULL GROUP BY status",
            null
        ).use { c ->
            val out = mutableListOf<Pair<String, Long>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1))
            return out
        }
    }
}
