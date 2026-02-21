package com.opentak.tracker.di

import android.content.Context
import com.opentak.tracker.cot.CotBuilder
import com.opentak.tracker.data.LogRepository
import com.opentak.tracker.data.SettingsRepository
import com.opentak.tracker.enrollment.CSREnrollmentManager
import com.opentak.tracker.security.CertificateStore
import com.opentak.tracker.service.LocationManagerWrapper
import com.opentak.tracker.service.TrackerEngine
import com.opentak.tracker.transport.ConnectionManager
import com.opentak.tracker.transport.TakUdpBroadcaster
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // All dependencies use constructor injection via @Inject @Singleton
    // This module exists for any future manual bindings
}
