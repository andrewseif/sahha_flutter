package com.sahha.flutter

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.NonNull
import com.google.gson.Gson
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

import sdk.sahha.android.source.*

/** SahhaFlutterPlugin */
class SahhaFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {

  enum class SahhaMethod {
    configure,
    authenticate,
    getDemographic,
    postDemographic,
    activityStatus,
    activate,
    postSensorData,
    analyze,
    openAppSettings
  }

  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel
  private var activity: Activity? = null
  private lateinit var context: Context

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "sahha_flutter")
    channel.setMethodCallHandler(this)
    context = flutterPluginBinding.applicationContext
    Log.d("Sahha", context.toString())
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onDetachedFromActivity() {
    Log.d("Sahha", "onDetachedFromActivity")
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    Log.d("Sahha", "onReattachedToActivityForConfigChanges")
    onAttachedToActivity(binding)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.d("Sahha", "onAttachedToActivity")
    activity = binding.activity
    Log.d("Sahha", activity.toString())
  }

  override fun onDetachedFromActivityForConfigChanges() {
    Log.d("Sahha", "onDetachedFromActivityForConfigChanges")
    onDetachedFromActivity()
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    Log.d("Sahha", "method")

    when (call.method) {
      SahhaMethod.configure.name -> {configure(call, result)}
      SahhaMethod.authenticate.name -> {authenticate(call, result)}
      SahhaMethod.getDemographic.name -> {getDemographic(call, result)}
      SahhaMethod.postDemographic.name -> {postDemographic(call, result)}
      SahhaMethod.activityStatus.name -> {activityStatus(call, result)}
      SahhaMethod.activate.name -> {activate(call, result)}
      SahhaMethod.postSensorData.name -> {postSensorData(call, result)}
      SahhaMethod.analyze.name -> {analyze(call, result)}
      SahhaMethod.openAppSettings.name -> {openAppSettings(call, result)}
      else -> { result.notImplemented() }
    }
  }

  fun configure(@NonNull call: MethodCall, @NonNull result: Result) {

    val environment: String? = call.argument<String>("environment")
    val sensors: List<String>? = call.argument<List<String>>("sensors")
    var postSensorDataManually: Boolean? = call.argument<Boolean>("postSensorDataManually")
    if (environment != null && sensors != null && postSensorDataManually != null) {

      var sahhaEnvironment: SahhaEnvironment
      try {
        sahhaEnvironment = SahhaEnvironment.valueOf(environment)
      } catch(e: IllegalArgumentException) {
        result.error("Sahha Error", "SahhaFlutter.configure() environment parameter is not valid", null)
        return
      }

      var sahhaSensors: MutableSet<SahhaSensor> = mutableSetOf()
      try {
        sensors.forEach {
          var sensor = SahhaSensor.valueOf(it)
          sahhaSensors.add(sensor)
        }
      } catch(e: IllegalArgumentException) {
        result.error("Sahha Error", "SahhaFlutter.configure() sensor parameter is not valid", null)
        return
      }

      var settings = SahhaSettings(sahhaEnvironment,
              SahhaFramework.flutter,
              sahhaSensors,
              postSensorDataManually
      )

      try {
        Log.d("Sahha", activity.toString())
        Sahha.configure(activity as ComponentActivity, settings)
        result.success(true)
      } catch(e: IllegalArgumentException) {
        Log.e("Sahha", e.message ?: "Activity error")
        result.error("Sahha Error", "SahhaFlutter.configure() Android activity is not valid", null)
      }

    } else {
      result.error("Sahha Error", "SahhaFlutter.configure() parameters are not valid", null)
    }
  }

  private fun authenticate(@NonNull call: MethodCall, @NonNull result: Result) {
    val profileToken: String? = call.argument<String>("profileToken")
    val refreshToken: String? = call.argument<String>("refreshToken")
    if (profileToken != null && refreshToken != null) {
      Sahha.authenticate(profileToken,refreshToken) { error, success ->
        if (error != null) {
          result.error("Sahha Error", error, null)
        } else {
          result.success(success)
        }
      }
    } else {
      result.error("Sahha Error", "SahhaFlutter.authenticate() parameters are not valid", null)
    }
  }

  private fun getDemographic(@NonNull call: MethodCall, @NonNull result: Result) {
    Sahha.getDemographic() { error, demographic ->
      if (error != null) {
        result.error("Sahha Error", error, null)
      } else if (demographic != null) {
        val gson = Gson()
        val demographicJson: String = gson.toJson(demographic)
        Log.d("Sahha", demographicJson)
        result.success(demographicJson)
      } else {
        result.error("Sahha Error", "Sahha Demographic not available", null)
      }
    }
  }

  private fun postDemographic(@NonNull call: MethodCall, @NonNull result: Result) {
    val age: Int? = call.argument<Int>("age")
    val gender: String? = call.argument<String>("gender")
    val country: String? = call.argument<String>("country")
    val birthCountry: String? = call.argument<String>("birthCountry")

    var demographic = SahhaDemographic(age, gender, country, birthCountry)
    Sahha.postDemographic(demographic) { error, success ->
      if (error != null) {
        result.error("Sahha Error", error, null)
      } else {
        result.success(success)
      }
    }
  }

  private fun activityStatus(@NonNull call: MethodCall, @NonNull result: Result) {

    var activityName: String? = call.argument<String>("activity")

    if (activityName != null) {
      try {
        var sahhaActivity = SahhaActivity.valueOf(activityName)
        when (sahhaActivity) {
          SahhaActivity.motion -> {
            result.success(Sahha.motion.activityStatus.ordinal)
          }
          else -> {
            result.error("Sahha Error", "SahhaFlutter activity parameter is not valid", null)
          }
        }
      } catch (e: IllegalArgumentException) {
        result.error("Sahha Error", "SahhaFlutter activity parameter is not valid", null)
      }
    } else {
      result.error("Sahha Error", "SahhaFlutter activity parameter is missing", null)
    }
  }

  private fun activate(@NonNull call: MethodCall, @NonNull result: Result) {

    var activityName: String? = call.argument<String>("activity")

    if (activityName != null) {
      try {
        var sahhaActivity = SahhaActivity.valueOf(activityName)
        when (sahhaActivity) {
          SahhaActivity.motion -> {
            Sahha.motion.activate { newStatus ->
              result.success(newStatus.ordinal)
            }
          }
          else -> {
            result.error("Sahha Error", "SahhaFlutter activity parameter is not valid", null)
          }
        }
      } catch (e: IllegalArgumentException) {
        result.error("Sahha Error", "SahhaFlutter activity parameter is not valid", null)
      }
    } else {
      result.error("Sahha Error", "SahhaFlutter activity parameter is missing", null)
    }
  }

  private fun postSensorData(@NonNull call: MethodCall, @NonNull result: Result) {

    val sensors: List<String>? = call.argument<List<String>>("sensors")
    var sahhaSensors: MutableSet<SahhaSensor>?
    if (sensors != null) {
      sahhaSensors = mutableSetOf()
      try {
        sensors.forEach {
          var sensor = SahhaSensor.valueOf(it)
          sahhaSensors.add(sensor)
        }
      } catch (e: IllegalArgumentException) {
        result.error("Sahha Error", "SahhaFlutter.postSensorData() sensor parameter is not valid", null)
        return
      }
    }

    /*
    Sahha.postSensorData(sahhaSensors) { error, success ->
    }
     */
    result.success(true)
  }

  private fun analyze(@NonNull call: MethodCall, @NonNull result: Result) {
    Sahha.analyze() { error, value ->
      if (error != null) {
        result.error("Sahha Error", error, null)
      } else if (value != null) {
        result.success(value)
      } else {
        result.error("Sahha Error", "Sahha Analyzation not available", null)
      }
    }
  }

  private fun openAppSettings(@NonNull call: MethodCall, @NonNull result: Result) {
    Sahha.motion.promptUserToActivate { _ ->
      result.success(true)
    }
  }

}
