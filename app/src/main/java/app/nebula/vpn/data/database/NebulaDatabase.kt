package app.nebula.vpn.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.nebula.vpn.data.database.dao.GroupDao
import app.nebula.vpn.data.database.dao.ProfileDao
import app.nebula.vpn.data.database.dao.RuleDao
import app.nebula.vpn.data.database.dao.SubscriptionDao
import app.nebula.vpn.data.database.dao.ThemeDao
import app.nebula.vpn.data.database.dao.TrafficStatDao
import app.nebula.vpn.data.database.entity.GroupEntity
import app.nebula.vpn.data.database.entity.ProfileEntity
import app.nebula.vpn.data.database.entity.RuleEntity
import app.nebula.vpn.data.database.entity.SubscriptionEntity
import app.nebula.vpn.data.database.entity.ThemeEntity
import app.nebula.vpn.data.database.entity.TrafficStatEntity

@Database(
    entities = [
        ProfileEntity::class,
        SubscriptionEntity::class,
        GroupEntity::class,
        RuleEntity::class,
        TrafficStatEntity::class,
        ThemeEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NebulaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun groupDao(): GroupDao
    abstract fun ruleDao(): RuleDao
    abstract fun trafficStatDao(): TrafficStatDao
    abstract fun themeDao(): ThemeDao
}
