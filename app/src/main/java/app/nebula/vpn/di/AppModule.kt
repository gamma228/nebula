package app.nebula.vpn.di

import android.content.Context
import androidx.room.Room
import app.nebula.vpn.core.proxy.CoreFactory
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.data.database.NebulaDatabase
import app.nebula.vpn.data.database.dao.GroupDao
import app.nebula.vpn.data.database.dao.ProfileDao
import app.nebula.vpn.data.database.dao.RuleDao
import app.nebula.vpn.data.database.dao.SubscriptionDao
import app.nebula.vpn.data.database.dao.ThemeDao
import app.nebula.vpn.data.database.dao.TrafficStatDao
import app.nebula.vpn.data.datastore.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN packetEncoding TEXT NOT NULL DEFAULT 'xudp'")
            db.execSQL("ALTER TABLE profiles ADD COLUMN headerType TEXT NOT NULL DEFAULT ''")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NebulaDatabase {
        return Room.databaseBuilder(
            context,
            NebulaDatabase::class.java,
            "nebula.db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(db: NebulaDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSubscriptionDao(db: NebulaDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideGroupDao(db: NebulaDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideRuleDao(db: NebulaDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideTrafficStatDao(db: NebulaDatabase): TrafficStatDao = db.trafficStatDao()

    @Provides
    fun provideThemeDao(db: NebulaDatabase): ThemeDao = db.themeDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideProxyCore(@ApplicationContext context: Context): ProxyCore {
        return CoreFactory.create(context, useStub = false)
    }

    @Provides
    @Singleton
    fun provideVpnServiceController(impl: app.nebula.vpn.core.connection.DefaultVpnServiceController): app.nebula.vpn.core.connection.VpnServiceController = impl

    @Provides
    @Singleton
    fun provideConnectionManager(impl: app.nebula.vpn.core.connection.DefaultConnectionManager): app.nebula.vpn.core.connection.ConnectionManager = impl

    @Provides
    @Singleton
    fun provideLatencyProbe(impl: app.nebula.vpn.core.proxy.DefaultLatencyProbe): app.nebula.vpn.core.proxy.LatencyProbe = impl

    @Provides
    @Singleton
    fun provideLatencyTester(impl: app.nebula.vpn.core.proxy.DefaultLatencyTester): app.nebula.vpn.core.proxy.LatencyTester = impl
}
