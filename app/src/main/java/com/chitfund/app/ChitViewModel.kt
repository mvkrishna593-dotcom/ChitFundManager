package com.chitfund.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ChitViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    fun chits(role: String) = dao.chits(role)
    fun chit(id: Long) = dao.chit(id)
    fun members(chitId: Long) = dao.members(chitId)
    fun auctions(chitId: Long) = dao.auctions(chitId)
    fun payments(chitId: Long) = dao.payments(chitId)

    fun addChit(c: Chit) { viewModelScope.launch { dao.insertChit(c) } }
    fun updateChit(c: Chit) { viewModelScope.launch { dao.updateChit(c) } }
    fun deleteChit(id: Long) { viewModelScope.launch { dao.deleteChitFully(id) } }

    fun addMember(chitId: Long, name: String, phone: String, place: String = "") {
        viewModelScope.launch { dao.insertMember(Member(chitId = chitId, name = name, phone = phone, place = place)) }
    }
    fun removeMember(m: Member) { viewModelScope.launch { dao.removeMember(m.id) } }

    fun saveAuction(a: Auction) { viewModelScope.launch { dao.upsertAuction(a) } }
    fun deleteAuction(chitId: Long, month: Int) { viewModelScope.launch { dao.deleteAuction(chitId, month) } }

    fun setPaid(chitId: Long, memberId: Long, month: Int, paid: Boolean, paidMillis: Long? = null) {
        viewModelScope.launch {
            dao.upsertPayment(
                Payment(chitId = chitId, memberId = memberId, monthNo = month, paid = paid, paidMillis = paidMillis)
            )
        }
    }
}
