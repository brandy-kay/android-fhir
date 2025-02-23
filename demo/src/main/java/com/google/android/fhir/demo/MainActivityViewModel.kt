/*
 * Copyright 2023-2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.demo

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import com.google.android.fhir.demo.data.DemoFhirSyncWorker
import com.google.android.fhir.sync.CurrentSyncJobStatus
import com.google.android.fhir.sync.PeriodicSyncConfiguration
import com.google.android.fhir.sync.PeriodicSyncJobStatus
import com.google.android.fhir.sync.RepeatInterval
import com.google.android.fhir.sync.Sync
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** View model for [MainActivity]. */
@OptIn(InternalCoroutinesApi::class)
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  private val _lastSyncTimestampLiveData = MutableLiveData<String>()
  val lastSyncTimestampLiveData: LiveData<String>
    get() = _lastSyncTimestampLiveData

  private val _pollState = MutableSharedFlow<CurrentSyncJobStatus>()
  val pollState: Flow<CurrentSyncJobStatus>
    get() = _pollState

  private val _pollPeriodicSyncJobStatus = MutableSharedFlow<PeriodicSyncJobStatus>()
  val pollPeriodicSyncJobStatus: Flow<PeriodicSyncJobStatus>
    get() = _pollPeriodicSyncJobStatus

  init {
    viewModelScope.launch {
      Sync.periodicSync<DemoFhirSyncWorker>(
          application.applicationContext,
          periodicSyncConfiguration =
            PeriodicSyncConfiguration(
              syncConstraints = Constraints.Builder().build(),
              repeat = RepeatInterval(interval = 15, timeUnit = TimeUnit.MINUTES),
            ),
        )
        .shareIn(this, SharingStarted.Eagerly, 10)
        .collect { _pollPeriodicSyncJobStatus.emit(it) }
    }
  }

  private var oneTimeSyncJob: Job? = null

  fun triggerOneTimeSync() {
    // Cancels any ongoing sync job before starting a new one. Since this function may be called
    // more than once, not canceling the ongoing job could result in the creation of multiple jobs
    // that emit the same object.
    oneTimeSyncJob?.cancel()
    oneTimeSyncJob =
      viewModelScope.launch {
        Sync.oneTimeSync<DemoFhirSyncWorker>(getApplication())
          .shareIn(this, SharingStarted.Eagerly, 0)
          .collect { result -> result.let { _pollState.emit(it) } }
      }
  }

  /** Emits last sync time. */
  fun updateLastSyncTimestamp(lastSync: OffsetDateTime? = null) {
    val formatter =
      DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(getApplication())) formatString24 else formatString12,
      )
    _lastSyncTimestampLiveData.value =
      lastSync?.let { it.toLocalDateTime()?.format(formatter) ?: "" }
        ?: Sync.getLastSyncTimestamp(getApplication())?.toLocalDateTime()?.format(formatter) ?: ""
  }

  companion object {
    private const val formatString24 = "yyyy-MM-dd HH:mm:ss"
    private const val formatString12 = "yyyy-MM-dd hh:mm:ss a"
  }
}
