package com.google.ads.mediation.fyber;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import com.fyber.inneractive.sdk.external.*;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationBannerAdapter;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;
import com.google.android.gms.ads.mediation.VersionInfo;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Fyber's official AdMob 3rd party adapter class. Implements Banners and Interstitials by
 * implementing the {@link MediationBannerAdapter} and {@link MediationInterstitialAdapter}
 * interfaces. Implements initialization and Rewarded video ads, by extending the {@link Adapter}
 * class.
 */
public class FyberMediationAdapter extends Adapter
    implements MediationBannerAdapter, MediationInterstitialAdapter {

  /**
   * Adapter class name for logging.
   */
  static final String TAG = FyberMediationAdapter.class.getSimpleName();

  /**
   * Fyber requires to know the host mediation platform.
   */
  private final static InneractiveMediationName MEDIATOR_NAME = InneractiveMediationName.ADMOB;

  /**
   * Key to obtain App id, required for initializing Fyber's SDK.
   */
  private static final String KEY_APP_ID = "applicationId";

  /**
   * Key to obtain a placement name or spot id. Required for creating a Fyber ad request.
   */
  static final String KEY_SPOT_ID = "spotId";

  /**
   * Fyber's Spot object for the banner.
   */
  private InneractiveAdSpot mBannerSpot;

  /**
   * Holds the banner view which is created by Fyber, in order to return when AdMob calls getView.
   */
  private ViewGroup mBannerWrapperView;

  /**
   * AdMob's external Banner listener.
   */
  private MediationBannerListener mMediationBannerListener;

  /**
   * AdMob's external Interstitial listener.
   */
  private MediationInterstitialListener mMediationInterstitialListener;

  /**
   * The context which was passed by AdMob to {@link #requestInterstitialAd}.
   */
  private WeakReference<Context> mInterstitialContext;

  /**
   * Fyber's spot object for interstitial.
   */
  private InneractiveAdSpot mInterstitialSpot;

  /**
   * Default Constructor.
   */
  public FyberMediationAdapter() {
  }

  /**
   * Only rewarded ads are implemented using the new Adapter interface.
   */
  public void loadRewardedAd(final MediationRewardedAdConfiguration configuration,
                             final MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> callback) {
    // Sometimes loadRewardedAd is called before initialize is called.

    String keyAppID = configuration.getServerParameters().getString(KEY_APP_ID);
    if (TextUtils.isEmpty(keyAppID)) {
      String logMessage = "Failed to initialize: SDK requires server parameters.";
      callback.onFailure(logMessage);
      return;
    }
    //the callback will return immediately with "SUCCESS" if already initialized.
    InneractiveAdManager.initialize(configuration.getContext(), keyAppID, new OnFyberMarketplaceInitializedListener() {
      @Override
      public void onFyberMarketplaceInitialized(FyberInitStatus fyberInitStatus) {
        //Special note regarding the FAILED status - the SDK will retry to initialize itself when an ad request is being made
        //So we ignore that status and let the ad request continue.
        if (fyberInitStatus == FyberInitStatus.FAILED_NO_KITS_DETECTED || fyberInitStatus == FyberInitStatus.INVALID_APP_ID) {
          callback.onFailure(String.format("Fyber Marketplace has failed to initialize - %s", fyberInitStatus));
        } else {
          FyberRewardedVideoRenderer rewardedVideoRenderer =
                  new FyberRewardedVideoRenderer(configuration, callback);
          rewardedVideoRenderer.render();
        }
      }
    });
  }

  @Override
  public void initialize(Context context,
      final InitializationCompleteCallback completionCallback,
      List<MediationConfiguration> mediationConfigurations) {
    // Initialize only once.
    if (InneractiveAdManager.wasInitialized()) {
      completionCallback.onInitializationSucceeded();
      return;
    }

    List<String> configuredAppIds = new ArrayList<>();
    for (MediationConfiguration configuration : mediationConfigurations) {
      Bundle serverParameters = configuration.getServerParameters();
      if (serverParameters == null) {
        continue;
      }

      String appId = serverParameters.getString(KEY_APP_ID);
      if (!TextUtils.isEmpty(appId)) {
        configuredAppIds.add(appId);
      }
    }

    if (configuredAppIds.isEmpty()) {
      String logMessage = "Failed to initialize: " +
          "Fyber SDK requires an appId to be configured on the AdMob UI.";
      Log.w(TAG, logMessage);
      if (completionCallback != null) {
        completionCallback.onInitializationFailed(logMessage);
      }
      return;
    }

    // We can only use one app id.
    String appIdForInitialization = configuredAppIds.get(0);

    if (configuredAppIds.size() > 1) {
      String message = String.format("Multiple '%s' entries found: %s. " +
              "Using '%s' to initialize the Fyber Marketplace SDK.",
          KEY_APP_ID, appIdForInitialization, appIdForInitialization);
      Log.w(TAG, message);
    }

    InneractiveAdManager.initialize(context, appIdForInitialization, new OnFyberMarketplaceInitializedListener() {
      @Override
      public void onFyberMarketplaceInitialized(FyberInitStatus fyberInitStatus) {
        //Special note regarding the FAILED status - the SDK will retry to initialize itself when an ad request is being made
        //So we ignore that status and let the ad request continue.
        if (fyberInitStatus == FyberInitStatus.FAILED_NO_KITS_DETECTED || fyberInitStatus == FyberInitStatus.INVALID_APP_ID) {
          completionCallback.onInitializationFailed(String.format("Fyber Marketplace SDK initialization failed, status - %s", fyberInitStatus));
        } else {
          completionCallback.onInitializationSucceeded();
        }
      }
    });
  }

  public VersionInfo getVersionInfo() {
    String versionString = BuildConfig.VERSION_NAME;
    String[] splits = versionString.split("\\.");

    if (splits.length >= 4) {
      int major = Integer.parseInt(splits[0]);
      int minor = Integer.parseInt(splits[1]);
      int micro = Integer.parseInt(splits[2]) * 100 + Integer.parseInt(splits[3]);
      return new VersionInfo(major, minor, micro);
    }

    String logMessage = String.format("Unexpected adapter version format: %s." +
        "Returning 0.0.0 for adapter version.", versionString);
    Log.w(TAG, logMessage);
    return new VersionInfo(0, 0, 0);
  }

  public VersionInfo getSDKVersionInfo() {
    String sdkVersion = InneractiveAdManager.getVersion();
    String[] splits = sdkVersion.split("\\.");

    if (splits.length >= 3) {
      int major = Integer.parseInt(splits[0]);
      int minor = Integer.parseInt(splits[1]);
      int micro = Integer.parseInt(splits[2]);
      return new VersionInfo(major, minor, micro);
    }

    String logMessage = String.format("Unexpected SDK version format: %s." +
        "Returning 0.0.0 for SDK version.", sdkVersion);
    Log.w(TAG, logMessage);
    return new VersionInfo(0, 0, 0);
  }

  /**
   * {@link MediationBannerAdapter} implementation.
   */
  @Override
  public void requestBannerAd(final Context context,
      final MediationBannerListener mediationBannerListener,
      Bundle serverParameters, AdSize adSize,
      MediationAdRequest mediationAdRequest, Bundle mediationExtras) {

    mMediationBannerListener = mediationBannerListener;

    String keyAppId = serverParameters.getString(KEY_APP_ID);
    if (TextUtils.isEmpty(keyAppId)) {
      mMediationBannerListener.onAdFailedToLoad(FyberMediationAdapter.this,
          AdRequest.ERROR_CODE_INVALID_REQUEST);
      return;
    }

    // Check that we got a valid Spot ID from the server.
    final String spotId = serverParameters.getString(FyberMediationAdapter.KEY_SPOT_ID);
    if (TextUtils.isEmpty(spotId)) {
      mMediationBannerListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
      Log.e(TAG, "Cannot render banner ad. Please define a valid spot id on the AdMob UI.");
      return;
    }

    InneractiveAdManager.initialize(context, keyAppId, new OnFyberMarketplaceInitializedListener() {
      @Override
      public void onFyberMarketplaceInitialized(FyberInitStatus fyberInitStatus) {
        if (fyberInitStatus == FyberInitStatus.FAILED_NO_KITS_DETECTED || fyberInitStatus == FyberInitStatus.INVALID_APP_ID) {
          mMediationBannerListener.onAdFailedToLoad(FyberMediationAdapter.this, AdRequest.ERROR_CODE_NETWORK_ERROR);
        } else {
          postInitializeRequestBanner(spotId, context);
        }
      }
    });

  }

  private void postInitializeRequestBanner(String spotId, Context context) {
    mBannerSpot = InneractiveAdSpotManager.get().createSpot();
    mBannerSpot.setMediationName(MEDIATOR_NAME);

    InneractiveAdViewUnitController controller = new InneractiveAdViewUnitController();
    mBannerSpot.addUnitController(controller);

    // Prepare wrapper view before making request.
    mBannerWrapperView = new RelativeLayout(context);

    InneractiveAdSpot.RequestListener requestListener = createFyberBannerAdListener();
    mBannerSpot.setRequestListener(requestListener);

    InneractiveAdRequest request = new InneractiveAdRequest(spotId);
    mBannerSpot.requestAd(request);
  }


  @Override
  public View getBannerView() {
    return mBannerWrapperView;
  }

  @Override
  public void onDestroy() {
    if (mBannerSpot != null) {
      mBannerSpot.destroy();
      mBannerSpot = null;
    }

    if (mInterstitialSpot != null) {
      mInterstitialSpot.destroy();
      mInterstitialSpot = null;
    }

    if (mInterstitialContext != null) {
      mInterstitialContext.clear();
      mInterstitialContext = null;
    }
  }

  @Override
  public void onPause() {
    // No relevant action. Refresh is disabled for banners.
  }

  @Override
  public void onResume() {
    // No relevant action. Refresh is disabled for banners.
  }

  /**
   * Creates Fyber's banner ad request listener.
   *
   * @return the created request listener.
   */
  @NonNull
  private InneractiveAdSpot.RequestListener createFyberBannerAdListener() {
    return new InneractiveAdSpot.RequestListener() {
      @Override
      public void onInneractiveSuccessfulAdRequest(InneractiveAdSpot inneractiveAdSpot) {
        // Just a double check that we have the right type of selected controller.
        if (!(mBannerSpot.getSelectedUnitController() instanceof
            InneractiveAdViewUnitController)) {
          mMediationBannerListener
              .onAdFailedToLoad(FyberMediationAdapter.this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
          mBannerSpot.destroy();
        }

        InneractiveAdViewUnitController controller =
            (InneractiveAdViewUnitController) mBannerSpot.getSelectedUnitController();

        InneractiveAdViewEventsListener listener = createFyberAdViewListener();
        controller.setEventsListener(listener);
        // Bind the wrapper view.
        controller.bindView(mBannerWrapperView);
        mMediationBannerListener.onAdLoaded(FyberMediationAdapter.this);
      }

      @Override
      public void onInneractiveFailedAdRequest(InneractiveAdSpot inneractiveAdSpot,
          InneractiveErrorCode inneractiveErrorCode) {
        // Convert Fyber's Marketplace error code into AdMob's AdRequest error code.
        int adMobErrorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR;
        if (inneractiveErrorCode == InneractiveErrorCode.CONNECTION_ERROR
            || inneractiveErrorCode == InneractiveErrorCode.CONNECTION_TIMEOUT) {
          adMobErrorCode = AdRequest.ERROR_CODE_NETWORK_ERROR;
        } else if (inneractiveErrorCode == InneractiveErrorCode.NO_FILL) {
          adMobErrorCode = AdRequest.ERROR_CODE_NO_FILL;
        }

        mMediationBannerListener.onAdFailedToLoad(FyberMediationAdapter.this, adMobErrorCode);
      }
    };
  }

  /**
   * When an ad is fetched successfully, creates a listener for Fyber's AdView events.
   *
   * @return the create events listener.
   */
  @NonNull
  private InneractiveAdViewEventsListener createFyberAdViewListener() {

    return new InneractiveAdViewEventsListenerAdapter() {
      @Override
      public void onAdImpression(InneractiveAdSpot adSpot) {
        // Nothing to report back here.
      }

      @Override
      public void onAdExpanded(InneractiveAdSpot inneractiveAdSpot) {
        super.onAdExpanded(inneractiveAdSpot);
        mMediationBannerListener.onAdOpened(FyberMediationAdapter.this);
      }

      @Override
      public void onAdCollapsed(InneractiveAdSpot inneractiveAdSpot) {
        super.onAdCollapsed(inneractiveAdSpot);
        mMediationBannerListener.onAdClosed(FyberMediationAdapter.this);
      }

      @Override
      public void onAdClicked(InneractiveAdSpot adSpot) {
        mMediationBannerListener.onAdClicked(FyberMediationAdapter.this);
        //we call ad opened since we open an "overlay" - our internal browser activity.
        mMediationBannerListener.onAdOpened(FyberMediationAdapter.this);
      }

      @Override
      public void onAdWillCloseInternalBrowser(InneractiveAdSpot inneractiveAdSpot) {
        super.onAdWillCloseInternalBrowser(inneractiveAdSpot);
        mMediationBannerListener.onAdClosed(FyberMediationAdapter.this);
      }

      @Override
      public void onAdWillOpenExternalApp(InneractiveAdSpot adSpot) {
        //https://ads-developers.googleblog.com/2014/05/google-mobile-ads-sdk-note-on-ad-click.html
        //note - we are missing an ad closed when the app is returned to foreground, and that's why we don't call adOpened here.
        mMediationBannerListener.onAdLeftApplication(FyberMediationAdapter.this);
      }
    };
  }

  /**
   * {@link MediationInterstitialAdapter} implementation.
   */
  @Override
  public void requestInterstitialAd(final Context context,
                                    final MediationInterstitialListener mediationInterstitialListener,
                                    Bundle serverParameters,
                                    MediationAdRequest mediationAdRequest,
                                    Bundle mediationExtras) {

    mMediationInterstitialListener = mediationInterstitialListener;

    String keyAppId = serverParameters.getString(KEY_APP_ID);
    if (TextUtils.isEmpty(keyAppId)) {
      mMediationInterstitialListener
          .onAdFailedToLoad(FyberMediationAdapter.this, AdRequest.ERROR_CODE_INVALID_REQUEST);
      return;
    }

    // Check that we got a valid spot id from the server.
    final String spotId = serverParameters.getString(FyberMediationAdapter.KEY_SPOT_ID);
    if (TextUtils.isEmpty(spotId)) {
      Log.w(TAG, "Cannot render interstitial ad. " +
              "Please define a valid spot id on the AdMob UI.");
      mMediationInterstitialListener.onAdFailedToLoad(this,
              AdRequest.ERROR_CODE_INVALID_REQUEST);
      return;
    }

    InneractiveAdManager.initialize(context, keyAppId, new OnFyberMarketplaceInitializedListener() {

      @Override
      public void onFyberMarketplaceInitialized(FyberInitStatus fyberInitStatus) {
        if (fyberInitStatus == FyberInitStatus.FAILED_NO_KITS_DETECTED || fyberInitStatus == FyberInitStatus.INVALID_APP_ID) {
          mMediationInterstitialListener.onAdFailedToLoad(FyberMediationAdapter.this, AdRequest.ERROR_CODE_NETWORK_ERROR);
        } else {
          postInitializeRequestInterstitial(spotId, context);
        }
      }

    });
  }

  private void postInitializeRequestInterstitial(String spotId, Context context) {
    // Cache the context for showInterstitial.
    mInterstitialContext = new WeakReference<>(context);

    mInterstitialSpot = InneractiveAdSpotManager.get().createSpot();
    mInterstitialSpot.setMediationName(MEDIATOR_NAME);

    InneractiveFullscreenUnitController controller = new InneractiveFullscreenUnitController();
    mInterstitialSpot.addUnitController(controller);

    InneractiveAdSpot.RequestListener requestListener = createFyberInterstitialAdListener();
    mInterstitialSpot.setRequestListener(requestListener);

    InneractiveAdRequest request = new InneractiveAdRequest(spotId);
    mInterstitialSpot.requestAd(request);
  }


  @Override
  public void showInterstitial() {
    if (mInterstitialSpot.getSelectedUnitController() instanceof
        InneractiveFullscreenUnitController) {
      Context context = mInterstitialContext != null ? mInterstitialContext.get() : null;

      if (context != null) {
        ((InneractiveFullscreenUnitController) mInterstitialSpot
            .getSelectedUnitController()).show(context);
      } else {
        Log.w(TAG, "showInterstitial called, but context reference was lost.");
        mMediationInterstitialListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
      }
    } else {
      Log.w(TAG, "showInterstitial called, but spot is not ready for show? " +
          "Should never happen.");
      mMediationInterstitialListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
    }
  }

  @NonNull
  private InneractiveAdSpot.RequestListener createFyberInterstitialAdListener() {
    return new InneractiveAdSpot.RequestListener() {
      @Override
      public void onInneractiveSuccessfulAdRequest(InneractiveAdSpot adSpot) {
        if (!(mInterstitialSpot.getSelectedUnitController() instanceof
            InneractiveFullscreenUnitController)) {
          mMediationInterstitialListener
              .onAdFailedToLoad(FyberMediationAdapter.this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
          mInterstitialSpot.destroy();
        }

        InneractiveFullscreenUnitController controller =
            (InneractiveFullscreenUnitController) mInterstitialSpot.getSelectedUnitController();
        InneractiveFullscreenAdEventsListener listener = createFyberInterstitialListener();
        controller.setEventsListener(listener);

        mMediationInterstitialListener.onAdLoaded(FyberMediationAdapter.this);
      }

      @Override
      public void onInneractiveFailedAdRequest(InneractiveAdSpot adSpot,
          InneractiveErrorCode inneractiveErrorCode) {
        // Convert Fyber's Marketplace error code into AdMob's AdRequest error code
        int adMobErrorCode = AdRequest.ERROR_CODE_INTERNAL_ERROR;
        if (inneractiveErrorCode == InneractiveErrorCode.CONNECTION_ERROR
            || inneractiveErrorCode == InneractiveErrorCode.CONNECTION_TIMEOUT) {
          adMobErrorCode = AdRequest.ERROR_CODE_NETWORK_ERROR;
        } else if (inneractiveErrorCode == InneractiveErrorCode.NO_FILL) {
          adMobErrorCode = AdRequest.ERROR_CODE_NO_FILL;
        }

        mMediationInterstitialListener
            .onAdFailedToLoad(FyberMediationAdapter.this, adMobErrorCode);
      }
    };
  }

  /**
   * Creates a listener for Fyber's Interstitial events
   *
   * @return the created event listener.
   */
  @NonNull
  private InneractiveFullscreenAdEventsListener createFyberInterstitialListener() {
    return new InneractiveFullscreenAdEventsListenerAdapter() {
      @Override
      public void onAdImpression(InneractiveAdSpot adSpot) {
        mMediationInterstitialListener.onAdOpened(FyberMediationAdapter.this);
      }

      @Override
      public void onAdClicked(InneractiveAdSpot adSpot) {
        mMediationInterstitialListener.onAdClicked(FyberMediationAdapter.this);
      }

      @Override
      public void onAdDismissed(InneractiveAdSpot adSpot) {
        mMediationInterstitialListener.onAdClosed(FyberMediationAdapter.this);
      }

      @Override
      public void onAdWillOpenExternalApp(InneractiveAdSpot adSpot) {
        mMediationInterstitialListener.onAdLeftApplication(FyberMediationAdapter.this);
      }
    };
  }


}
