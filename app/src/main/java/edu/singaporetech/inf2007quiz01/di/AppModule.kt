package edu.singaporetech.inf2007quiz01.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import edu.singaporetech.inf2007quiz01.consensus.ConsensusEngine
import edu.singaporetech.inf2007quiz01.data.local.AppDatabase
import edu.singaporetech.inf2007quiz01.data.local.BlockDao
import edu.singaporetech.inf2007quiz01.data.local.HistoryDao
import edu.singaporetech.inf2007quiz01.data.local.PreferencesManager
import edu.singaporetech.inf2007quiz01.data.remote.MathJsApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/** Hilt DI module — wires up Room, DataStore, and Retrofit as singletons. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "calbot_database"
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    fun provideBlockDao(database: AppDatabase): BlockDao {
        return database.blockDao()
    }

    @Provides
    @Singleton
    fun provideConsensusEngine(
        blockDao: BlockDao,
        @ApplicationContext context: Context
    ): ConsensusEngine {
        return ConsensusEngine(blockDao, context)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideMathJsApi(): MathJsApi {
        return Retrofit.Builder()
            .baseUrl("https://api.mathjs.org/v4/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MathJsApi::class.java)
    }
}
