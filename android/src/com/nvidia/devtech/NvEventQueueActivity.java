package com.nvidia.devtech;

import java.util.Arrays;
import java.util.Comparator;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGL11;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import com.mapswithme.maps.R;
import com.mapswithme.maps.base.MapsWithMeBaseActivity;
import com.mapswithme.util.log.Logger;
import com.mapswithme.util.log.StubLogger;

public abstract class NvEventQueueActivity extends MapsWithMeBaseActivity
{
  private static final String TAG = "NvEventQueueActivity";

  private static final int EGL_RENDERABLE_TYPE = 0x3040;
  private static final int EGL_OPENGL_ES2_BIT = 0x0004;
  private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

  private final Logger mLog = StubLogger.get();

  private final EGL10 m_egl = (EGL10) EGLContext.getEGL();
  protected boolean m_eglInitialized = false;
  protected EGLSurface m_eglSurface = null;
  protected EGLDisplay m_eglDisplay = null;
  protected EGLContext m_eglContext = null;
  protected EGLConfig m_eglConfig = null;

  protected SurfaceHolder m_cachedSurfaceHolder = null;
  private int m_surfaceWidth = 0;
  private int m_surfaceHeight = 0;

  private int m_displayDensity = 0;

  private int m_fixedWidth = 0;
  private int m_fixedHeight = 0;

  private boolean m_nativeLaunched = false;

  public void setFixedSize(int fw, int fh)
  {
    m_fixedWidth = fw;
    m_fixedHeight = fh;
  }

  public int getDisplayDensity()
  {
    return m_displayDensity;
  }

  public int getSurfaceWidth()
  {
    return m_surfaceWidth;
  }

  public int getSurfaceHeight()
  {
    return m_surfaceHeight;
  }

  protected native boolean onCreateNative();

  protected native boolean onStartNative();

  protected native boolean onRestartNative();

  protected native boolean onResumeNative();

  protected native boolean onSurfaceCreatedNative(int w, int h, int density);

  protected native boolean onFocusChangedNative(boolean focused);

  protected native boolean onSurfaceChangedNative(int w, int h, int density);

  protected native boolean onSurfaceDestroyedNative();

  protected native boolean onPauseNative();

  protected native boolean onStopNative();

  protected native boolean onDestroyNative();

  public native boolean multiTouchEvent(int action, boolean hasFirst,
      boolean hasSecond, int x0, int y0, int x1, int y1, MotionEvent event);

  @SuppressWarnings("deprecation")
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    final SurfaceView surfaceView = (SurfaceView) findViewById(R.id.map_surfaceview);

    final SurfaceHolder holder = surfaceView.getHolder();
    holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);

    final DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    m_displayDensity = metrics.densityDpi;

    holder.addCallback(new Callback()
    {
      @Override
      public void surfaceCreated(SurfaceHolder holder)
      {
        m_cachedSurfaceHolder = holder;
        if (m_fixedWidth != 0 && m_fixedHeight != 0)
          holder.setFixedSize(m_fixedWidth, m_fixedHeight);
        onSurfaceCreatedNative(m_surfaceWidth, m_surfaceHeight, getDisplayDensity());
      }

      @Override
      public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
      {
        m_cachedSurfaceHolder = holder;
        m_surfaceWidth = width;
        m_surfaceHeight = height;
        onSurfaceChangedNative(m_surfaceWidth, m_surfaceHeight, getDisplayDensity());
      }

      @Override
      public void surfaceDestroyed(SurfaceHolder holder)
      {
        m_cachedSurfaceHolder = null;
        onSurfaceDestroyedNative();
      }
    });

    m_nativeLaunched = true;
    onCreateNative();
  }

  @Override
  protected void onStart()
  {
    super.onStart();
    if (m_nativeLaunched)
      onStartNative();
  }

  @Override
  protected void onRestart()
  {
    super.onRestart();
    if (m_nativeLaunched)
      onRestartNative();
  }

  @Override
  protected void onResume()
  {
    super.onResume();
    if (m_nativeLaunched)
      onResumeNative();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus)
  {
    if (m_nativeLaunched)
      onFocusChangedNative(hasFocus);
    super.onWindowFocusChanged(hasFocus);
  }

  @Override
  protected void onPause()
  {
    super.onPause();
    if (m_nativeLaunched)
      onPauseNative();
  }

  @Override
  protected void onStop()
  {
    super.onStop();
    if (m_nativeLaunched)
      onStopNative();
  }

  @Override
  public void onDestroy()
  {
    super.onDestroy();
    if (m_nativeLaunched)
    {
      onDestroyNative();
      CleanupEGL();
    }
  }

  private int m_lastPointerId = 0;


  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    final int count = event.getPointerCount();

    if (!m_nativeLaunched || count == 0)
      return super.onTouchEvent(event);

    switch (count)
    {
    case 1:
    {
      m_lastPointerId = event.getPointerId(0);

      final int x0 = (int)event.getX();
      final int y0 = (int)event.getY();

      return multiTouchEvent(event.getAction(), true, false, x0, y0, 0, 0, event);
    }
    default:
    {
      final int x0 = (int)event.getX(0);
      final int y0 = (int)event.getY(0);

      final int x1 = (int)event.getX(1);
      final int y1 = (int)event.getY(1);

      if (event.getPointerId(0) == m_lastPointerId)
        return multiTouchEvent(event.getAction(), true, true,
            x0, y0, x1, y1, event);
      else
        return multiTouchEvent(event.getAction(), true, true, x1, y1, x0, y0, event);
    }
    }
  }

  String eglConfigToString(final EGLConfig config)
  {
    final int[] value = new int[1];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_RED_SIZE, value);
    final int red = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_GREEN_SIZE, value);
    final int green = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_BLUE_SIZE, value);
    final int blue = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_ALPHA_SIZE, value);
    final int alpha = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_STENCIL_SIZE, value);
    final int stencil = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_DEPTH_SIZE, value);
    final int depth = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_CONFIG_CAVEAT, value);
    final String caveat = (value[0] == EGL11.EGL_NONE) ? "EGL_NONE" :
      (value[0] == EGL11.EGL_SLOW_CONFIG) ? "EGL_SLOW_CONFIG" : "EGL_NON_CONFORMANT_CONFIG";
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_BUFFER_SIZE, value);
    final int buffer = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_LEVEL, value);
    final int level = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_SAMPLE_BUFFERS, value);
    final int sampleBuffers = value[0];
    m_egl.eglGetConfigAttrib(m_eglDisplay, config, EGL11.EGL_SAMPLES, value);
    final int samples = value[0];

    return "R" + red + "G" + green + "B" + blue + "A" + alpha +
        " Stencil:" + stencil + " Depth:" + depth + " Caveat:" + caveat +
        " BufferSize:" + buffer + " Level:" + level + " SampleBuffers:" + sampleBuffers +
        " Samples:" + samples;
  }


  /** The number of bits requested for the red component */
  protected int redSize = 5;
  /** The number of bits requested for the green component */
  protected int greenSize = 6;
  /** The number of bits requested for the blue component */
  protected int blueSize = 5;
  /** The number of bits requested for the alpha component */
  protected int alphaSize = 0;
  /** The number of bits requested for the stencil component */
  protected int stencilSize = 0;
  /** The number of bits requested for the depth component */
  protected int depthSize = 16;

  public class EGLConfigComparator implements Comparator<EGLConfig>
  {
    @Override
    public int compare(EGLConfig l, EGLConfig r)
    {
      final int [] value = new int[1];

      /// splitting by EGL_CONFIG_CAVEAT,
      /// firstly selecting EGL_NONE, then EGL_SLOW_CONFIG
      /// and then EGL_NON_CONFORMANT_CONFIG
      m_egl.eglGetConfigAttrib(m_eglDisplay, l, EGL11.EGL_CONFIG_CAVEAT, value);
      final int lcav = value[0];

      m_egl.eglGetConfigAttrib(m_eglDisplay, r, EGL11.EGL_CONFIG_CAVEAT, value);
      final int rcav = value[0];

      if (lcav != rcav)
      {
        int ltemp = 0;
        int rtemp = 0;

        switch (lcav)
        {
        case EGL11.EGL_NONE:
          ltemp = 0;
          break;
        case EGL11.EGL_SLOW_CONFIG:
          ltemp = 1;
          break;
        case EGL11.EGL_NON_CONFORMANT_CONFIG:
          ltemp = 2;
          break;
        };

        switch (rcav)
        {
        case EGL11.EGL_NONE:
          rtemp = 0;
          break;
        case EGL11.EGL_SLOW_CONFIG:
          rtemp = 1;
          break;
        case EGL11.EGL_NON_CONFORMANT_CONFIG:
          rtemp = 2;
        };

        return ltemp - rtemp;
      }
      return 0;
    }
  };

  int m_choosenConfigIndex = 0;
  EGLConfig[] m_configs = new EGLConfig[40];
  int m_actualConfigsNumber[] = new int[] {0};

  /**
   * Called to initialize EGL. This function should not be called by the
   * inheriting activity, but can be overridden if needed.
   *
   * @return True if successful
   */
  protected boolean InitEGL()
  {
    final int[] configAttrs = new int[] { EGL11.EGL_RED_SIZE, redSize,
                                          EGL11.EGL_GREEN_SIZE, greenSize,
                                          EGL11.EGL_BLUE_SIZE, blueSize,
                                          EGL11.EGL_ALPHA_SIZE, alphaSize,
                                          EGL11.EGL_STENCIL_SIZE, stencilSize,
                                          EGL11.EGL_DEPTH_SIZE, depthSize,
                                          EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                                          EGL11.EGL_NONE };

    m_eglDisplay = m_egl.eglGetDisplay(EGL11.EGL_DEFAULT_DISPLAY);
    if (m_eglDisplay == EGL11.EGL_NO_DISPLAY)
    {
      mLog.d(TAG, "eglGetDisplay failed");
      return false;
    }

    final int[] version = new int[2];
    if (!m_egl.eglInitialize(m_eglDisplay, version))
    {
      mLog.d(TAG, "eglInitialize failed with error " + m_egl.eglGetError());
      return false;
    }

    if (!m_egl.eglChooseConfig(m_eglDisplay, configAttrs, m_configs, m_configs.length, m_actualConfigsNumber))
    {
      mLog.d(TAG, "eglChooseConfig failed with error " + m_egl.eglGetError());
      return false;
    }

    if (m_actualConfigsNumber[0] == 0)
    {
      mLog.d(TAG, "eglChooseConfig returned zero configs");
      return false;
    }

    Arrays.sort(m_configs, 0, m_actualConfigsNumber[0], new EGLConfigComparator());

    m_choosenConfigIndex = 0;

    while (true)
    {
      m_eglConfig = m_configs[m_choosenConfigIndex];

      // Debug print
      mLog.d(TAG, "Matched egl configs:");
      for (int i = 0; i < m_actualConfigsNumber[0]; ++i)
         mLog.d(TAG, (i == m_choosenConfigIndex ? "*" : " ") + i + ": " + eglConfigToString(m_configs[i]));

      final int[] contextAttrs = new int[] { EGL_CONTEXT_CLIENT_VERSION, 2, EGL11.EGL_NONE };
      m_eglContext = m_egl.eglCreateContext(m_eglDisplay, m_eglConfig, EGL11.EGL_NO_CONTEXT, contextAttrs);
      if (m_eglContext == EGL11.EGL_NO_CONTEXT)
      {
        mLog.d(TAG, "eglCreateContext failed with error " + m_egl.eglGetError());
        m_choosenConfigIndex++;
      }
      else
        break;

      if (m_choosenConfigIndex == m_configs.length)
      {
        mLog.d(TAG, "No more configs left to choose");
        return false;
      }
    }

    m_eglInitialized = true;

    return true;
  }

  /**
   * Called to clean up m_egl. This function should not be called by the
   * inheriting activity, but can be overridden if needed.
   */
  protected boolean CleanupEGL()
  {
    mLog.d(TAG, "CleanupEGL");

    if (!m_eglInitialized)
      return false;

    if (!DestroySurfaceEGL())
      return false;

    if (m_eglDisplay != null)
      m_egl.eglMakeCurrent(m_eglDisplay, EGL11.EGL_NO_SURFACE,
          EGL11.EGL_NO_SURFACE, EGL11.EGL_NO_CONTEXT);
    if (m_eglContext != null)
    {
      mLog.d(TAG, "eglDestroyContext");
      m_egl.eglDestroyContext(m_eglDisplay, m_eglContext);
    }
    if (m_eglDisplay != null)
      m_egl.eglTerminate(m_eglDisplay);

    m_eglDisplay = null;
    m_eglContext = null;
    m_eglSurface = null;

    m_eglConfig = null;

    m_surfaceWidth = 0;
    m_surfaceHeight = 0;

    m_eglInitialized = false;

    return true;
  }

  protected boolean validateSurfaceSize(EGLSurface eglSurface)
  {
    final int sizes[] = new int[1];
    sizes[0] = 0;
    m_egl.eglQuerySurface(m_eglDisplay, eglSurface, EGL10.EGL_WIDTH, sizes);
    m_surfaceWidth = sizes[0];

    sizes[0] = 0;
    m_egl.eglQuerySurface(m_eglDisplay, eglSurface, EGL10.EGL_HEIGHT, sizes);
    m_surfaceHeight = sizes[0];

    mLog.d(TAG, "trying to get surface size to see if it's valid: surfaceSize= " + m_surfaceWidth + "x" + m_surfaceHeight);

    return m_surfaceWidth * m_surfaceHeight != 0;
  }

  protected boolean CreateSurfaceEGL()
  {
    if (m_cachedSurfaceHolder == null)
    {
      mLog.d(TAG, "createEGLSurface failed, m_cachedSurfaceHolder is null");
      return false;
    }

    if (!m_eglInitialized && (m_eglInitialized = InitEGL()))
    {
      mLog.d(TAG, "createEGLSurface failed, cannot initialize EGL");
      return false;
    }

    if (m_eglDisplay == null)
    {
      mLog.d(TAG, "createEGLSurface: display is null");
      return false;
    }
    else if (m_eglConfig == null)
    {
      mLog.d(TAG, "createEGLSurface: config is null");
      return false;
    }

    int choosenSurfaceConfigIndex = m_choosenConfigIndex;

    while (true)
    {
      /// trying to create window surface with one of the EGL configs, recreating the m_eglConfig if necessary

      m_eglSurface = m_egl.eglCreateWindowSurface(m_eglDisplay, m_configs[choosenSurfaceConfigIndex], m_cachedSurfaceHolder, null);

      final boolean surfaceCreated = (m_eglSurface != EGL11.EGL_NO_SURFACE);
      final boolean surfaceValidated = surfaceCreated ? validateSurfaceSize(m_eglSurface) : false;

      if (surfaceCreated && !surfaceValidated)
        m_egl.eglDestroySurface(m_eglDisplay, m_eglSurface);

      if (!surfaceCreated || !surfaceValidated)
      {
        mLog.d(TAG, "eglCreateWindowSurface failed for config : " + eglConfigToString(m_configs[choosenSurfaceConfigIndex]));
        choosenSurfaceConfigIndex += 1;
        if (choosenSurfaceConfigIndex == m_actualConfigsNumber[0])
        {
          m_eglSurface = null;
          mLog.d(TAG, "no eglConfigs left");
          break;
        }
        else
          mLog.d(TAG, "trying : " + eglConfigToString(m_configs[choosenSurfaceConfigIndex]));
      }
      else
        break;
    }

    if ((choosenSurfaceConfigIndex != m_choosenConfigIndex) && (m_eglSurface != null))
    {
      mLog.d(TAG, "window surface is created for eglConfig : " + eglConfigToString(m_configs[choosenSurfaceConfigIndex]));

      // unbinding context
      if (m_eglDisplay != null)
        m_egl.eglMakeCurrent(m_eglDisplay,
                             EGL11.EGL_NO_SURFACE,
                             EGL11.EGL_NO_SURFACE,
                             EGL11.EGL_NO_CONTEXT);

      // destroying context
      if (m_eglContext != null)
        m_egl.eglDestroyContext(m_eglDisplay, m_eglContext);

      // recreating context with same eglConfig as eglWindowSurface has
      final int[] contextAttrs = new int[] { EGL_CONTEXT_CLIENT_VERSION, 2, EGL11.EGL_NONE };
      m_eglContext = m_egl.eglCreateContext(m_eglDisplay, m_configs[choosenSurfaceConfigIndex], EGL11.EGL_NO_CONTEXT, contextAttrs);
      if (m_eglContext == EGL11.EGL_NO_CONTEXT)
      {
        mLog.d(TAG, "context recreation failed with error " + m_egl.eglGetError());
        return false;
      }

      m_choosenConfigIndex = choosenSurfaceConfigIndex;
      m_eglConfig = m_configs[m_choosenConfigIndex];
    }

    final int sizes[] = new int[1];
    m_egl.eglQuerySurface(m_eglDisplay, m_eglSurface, EGL10.EGL_WIDTH, sizes);
    m_surfaceWidth = sizes[0];
    m_egl.eglQuerySurface(m_eglDisplay, m_eglSurface, EGL10.EGL_HEIGHT, sizes);
    m_surfaceHeight = sizes[0];
    return true;
  }

  /**
   * Destroys the EGLSurface used for rendering. This function should not be
   * called by the inheriting activity, but can be overridden if needed.
   */
  protected boolean DestroySurfaceEGL()
  {
    if (m_eglDisplay != null && m_eglSurface != null)
      m_egl.eglMakeCurrent(m_eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, m_eglContext);
    if (m_eglSurface != null)
      m_egl.eglDestroySurface(m_eglDisplay, m_eglSurface);
    m_eglSurface = null;

    return true;
  }

  public boolean BindSurfaceAndContextEGL()
  {
    if (m_eglContext == null)
    {
      mLog.d(TAG, "m_eglContext is NULL");
      return false;
    }
    else if (m_eglSurface == null)
    {
      mLog.d(TAG, "m_eglSurface is NULL");
      return false;
    }
    else if (!m_egl.eglMakeCurrent(m_eglDisplay, m_eglSurface, m_eglSurface, m_eglContext))
    {
      mLog.d(TAG, "eglMakeCurrent err: " + m_egl.eglGetError());
      return false;
    }

    return true;
  }

  public boolean UnbindSurfaceAndContextEGL()
  {
    mLog.d(TAG, "UnbindSurfaceAndContextEGL");
    if (m_eglDisplay == null)
      return false;

    return m_egl.eglMakeCurrent(m_eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
  }

  public boolean SwapBuffersEGL()
  {
    if (m_eglSurface == null)
    {
      mLog.d(TAG, "m_eglSurface is NULL");
      return false;
    }
    else if (!m_egl.eglSwapBuffers(m_eglDisplay, m_eglSurface))
    {
      mLog.d(TAG, "eglSwapBufferrr: " + m_egl.eglGetError());
      return false;
    }
    return true;
  }

  public int GetErrorEGL()
  {
    return m_egl.eglGetError();
  }

  public void OnRenderingInitialized()
  {
  }

  public void ReportUnsupported()
  {
    Log.i(TAG, "this phone GPU is unsupported");
  }
}