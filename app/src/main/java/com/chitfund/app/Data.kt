package com.chitfund.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** role is "ORGANISER" or "PARTICIPANT". myWonMonth is only used for participants. */
@Entity
data class Chit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String,
    val members: Int,
    val monthlyAmount: Double,
    val commissionPercent: Double,
    val myWonMonth: Int? = null
)

@Entity
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chitId: Long,
    val name: String,
    val phone: String
)

@Entity(indices = [Index(value = ["chitId", "monthNo"], unique = true)])
data class Auction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chitId: Long,
    val monthNo: Int,
    val bid: Double,
    val winnerMemberId: Long? = null
)

@Entity(indices = [Index(value = ["chitId", "memberId", "monthNo"], unique = true)])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chitId: Long,
    val memberId: Long,
    val monthNo: Int,
    val paid: Boolean
)

@Dao
abstract class ChitDao {
    @Query("SELECT * FROM Chit WHERE role = :role ORDER BY id DESC")
    abstract fun chits(role: String): Flow<List<Chit>>

    @Query("SELECT * FROM Chit WHERE id = :id")
    abstract fun chit(id: Long): Flow<Chit?>

    @Insert abstract suspend fun insertChit(c: Chit): Long
    @Update abstract suspend fun updateChit(c: Chit)

    @Query("SELECT * FROM Member WHERE chitId = :chitId ORDER BY id")
    abstract fun members(chitId: Long): Flow<List<Member>>

    @Insert abstract suspend fun insertMember(m: Member)

    @Query("DELETE FROM Member WHERE id = :id")
    abstract suspend fun deleteMemberRow(id: Long)

    @Query("DELETE FROM Payment WHERE memberId = :memberId")
    abstract suspend fun deletePaymentsOfMember(memberId: Long)

    @Query("UPDATE Auction SET winnerMemberId = NULL WHERE winnerMemberId = :memberId")
    abstract suspend fun clearWinner(memberId: Long)

    @Transaction
    open suspend fun removeMember(memberId: Long) {
        deletePaymentsOfMember(memberId)
        clearWinner(memberId)
        deleteMemberRow(memberId)
    }

    @Query("SELECT * FROM Auction WHERE chitId = :chitId ORDER BY monthNo")
    abstract fun auctions(chitId: Long): Flow<List<Auction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAuction(a: Auction)

    @Query("DELETE FROM Auction WHERE chitId = :chitId AND monthNo = :monthNo")
    abstract suspend fun deleteAuction(chitId: Long, monthNo: Int)

    @Query("SELECT * FROM Payment WHERE chitId = :chitId")
    abstract fun payments(chitId: Long): Flow<List<Payment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPayment(p: Payment)

    @Query("DELETE FROM Member WHERE chitId = :id")
    abstract suspend fun deleteMembersOf(id: Long)

    @Query("DELETE FROM Auction WHERE chitId = :id")
    abstract suspend fun deleteAuctionsOf(id: Long)

    @Query("DELETE FROM Payment WHERE chitId = :id")
    abstract suspend fun deletePaymentsOf(id: Long)

    @Query("DELETE FROM Chit WHERE id = :id")
    abstract suspend fun deleteChitRow(id: Long)

    @Transaction
    open suspend fun deleteChitFully(id: Long) {
        deletePaymentsOf(id)
        deleteAuctionsOf(id)
        deleteMembersOf(id)
        deleteChitRow(id)
    }
}

@Database(
    entities = [Chit::class, Member::class, Auction::class, Payment::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): ChitDao

    companion object {
        @Volatile private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "chit.db")
                .build().also { inst = it }
        }
    }
}
