package com.example.mystorebox.utils

import com.example.mystorebox.data.local.InventoryDao.SheetsExportRecord
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoogleSheetsExporter {

    suspend fun exportInventory(accessToken: String, inventoryData: List<SheetsExportRecord>): String {
        return withContext(Dispatchers.IO) {
            val requestInitializer = HttpRequestInitializer { request ->
                request.headers.authorization = "Bearer $accessToken"
            }

            val sheetsService = Sheets.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
            ).setApplicationName("My Store Box").build()

            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val newSheet = Spreadsheet().apply {
                properties = SpreadsheetProperties().setTitle("Inventario Store Box - $dateStr")
            }

            val createdSheet = sheetsService.spreadsheets().create(newSheet).execute()
            val spreadsheetId = createdSheet.spreadsheetId
            val sheetId = createdSheet.sheets?.get(0)?.properties?.sheetId ?: 0

            val rowsData = mutableListOf<List<Any>>()
            rowsData.add(listOf("Caja", "Fila", "Carta", "Edición", "Cantidad"))

            inventoryData.forEach { record ->
                rowsData.add(
                    listOf(
                        record.boxName,
                        record.rowName,
                        record.cardName,
                        record.edition ?: "",
                        record.quantity
                    )
                )
            }

            val body = ValueRange().setValues(rowsData)
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            val requests = mutableListOf<Request>()

            requests.add(
                Request().setSetBasicFilter(
                    SetBasicFilterRequest().setFilter(
                        BasicFilter().setRange(
                            GridRange().setSheetId(sheetId).setStartRowIndex(0).setStartColumnIndex(0).setEndColumnIndex(5)
                        )
                    )
                )
            )

            requests.add(
                Request().setAddBanding(
                    AddBandingRequest().setBandedRange(
                        BandedRange().setRange(
                            GridRange().setSheetId(sheetId).setStartRowIndex(0).setStartColumnIndex(0).setEndColumnIndex(5)
                        ).setRowProperties(
                            BandingProperties()
                                .setHeaderColor(Color().setRed(0.2f).setGreen(0.2f).setBlue(0.2f))
                                .setFirstBandColor(Color().setRed(1f).setGreen(1f).setBlue(1f))
                                .setSecondBandColor(Color().setRed(0.95f).setGreen(0.95f).setBlue(0.95f))
                        )
                    )
                )
            )

            requests.add(
                Request().setRepeatCell(
                    RepeatCellRequest()
                        .setRange(
                            GridRange().setSheetId(sheetId).setStartRowIndex(0).setEndRowIndex(1).setStartColumnIndex(0).setEndColumnIndex(5)
                        )
                        .setCell(
                            CellData().setUserEnteredFormat(
                                CellFormat().setTextFormat(
                                    TextFormat().setBold(true).setForegroundColor(Color().setRed(1f).setGreen(1f).setBlue(1f))
                                )
                            )
                        )
                        .setFields("userEnteredFormat.textFormat")
                )
            )

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()

            createdSheet.spreadsheetUrl
        }
    }
}