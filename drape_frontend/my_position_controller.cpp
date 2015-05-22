#include "my_position_controller.hpp"
#include "visual_params.hpp"

#include "indexer/mercator.hpp"

#include "3party/Alohalytics/src/alohalytics.h"

namespace df
{

namespace
{

int const POSITION_Y_OFFSET = 120;
double const GPS_BEARING_LIFETIME_S = 5.0;
double const MIN_SPEED_THRESHOLD_MPS = 1.0;

uint16_t SetModeBit(uint16_t mode, uint16_t bit)
{
  return mode | bit;
}

//uint16_t ResetModeBit(uint16_t mode, uint16_t bit)
//{
//  return mode & (~bit);
//}

location::EMyPositionMode ResetAllModeBits(uint16_t mode)
{
  return (location::EMyPositionMode)(mode & 0xF);
}

uint16_t ChangeMode(uint16_t mode, location::EMyPositionMode newMode)
{
  return (mode & 0xF0) | newMode;
}

bool TestModeBit(uint16_t mode, uint16_t bit)
{
  return (mode & bit) != 0;
}

} // namespace

MyPositionController::MyPositionController(location::EMyPositionMode initMode)
  : m_modeInfo(location::MODE_PENDING_POSITION)
  , m_afterPendingMode(location::MODE_FOLLOW)
  , m_errorRadius(0.0)
  , m_position(m2::PointD::Zero())
  , m_drawDirection(0.0)
  , m_lastGPSBearing(false)
  , m_isVisible(false)
  , m_isDirtyViewport(false)
{
  if (initMode > location::MODE_UNKNOWN_POSITION)
    m_afterPendingMode = initMode;
  else
    m_modeInfo = location::MODE_UNKNOWN_POSITION;
}

void MyPositionController::SetListener(ref_ptr<MyPositionController::Listener> listener)
{
  m_listener = listener;
}

m2::PointD const & MyPositionController::Position() const
{
  return m_position;
}

double MyPositionController::GetErrorRadius() const
{
  return m_errorRadius;
}

bool MyPositionController::IsModeChangeViewport() const
{
  return GetMode() >= location::MODE_FOLLOW;
}

bool MyPositionController::IsModeHasPosition() const
{
  return GetMode() >= location::MODE_NOT_FOLLOW;
}

void MyPositionController::SetRenderShape(drape_ptr<MyPosition> && shape)
{
  m_shape = move(shape);
}

void MyPositionController::SetFixedZoom()
{
  SetModeInfo(SetModeBit(m_modeInfo, FixedZoomBit));
}

void MyPositionController::NextMode()
{
  string const kAlohalyticsClickEvent = "$onClick";
  location::EMyPositionMode currentMode = GetMode();
  location::EMyPositionMode newMode = currentMode;

  if (!IsInRouting())
  {
    switch (currentMode)
    {
    case location::MODE_UNKNOWN_POSITION:
      alohalytics::LogEvent(kAlohalyticsClickEvent, "@UnknownPosition");
      newMode = location::MODE_PENDING_POSITION;
      break;
    case location::MODE_PENDING_POSITION:
      alohalytics::LogEvent(kAlohalyticsClickEvent, "@PendingPosition");
      newMode = location::MODE_UNKNOWN_POSITION;
      m_afterPendingMode = location::MODE_FOLLOW;
      break;
    case location::MODE_NOT_FOLLOW:
      alohalytics::LogEvent(kAlohalyticsClickEvent, "@NotFollow");
      newMode = location::MODE_FOLLOW;
      break;
    case location::MODE_FOLLOW:
      alohalytics::LogEvent(kAlohalyticsClickEvent, "@Follow");
      if (IsRotationActive())
        newMode = location::MODE_ROTATE_AND_FOLLOW;
      else
      {
        newMode = location::MODE_UNKNOWN_POSITION;
        m_afterPendingMode = location::MODE_FOLLOW;
      }
      break;
    case location::MODE_ROTATE_AND_FOLLOW:
      alohalytics::LogEvent(kAlohalyticsClickEvent, "@RotateAndFollow");
      newMode = location::MODE_UNKNOWN_POSITION;
      m_afterPendingMode = location::MODE_FOLLOW;
      break;
    }
  }
  else
  {
    newMode = IsRotationActive() ? location::MODE_ROTATE_AND_FOLLOW : location::MODE_FOLLOW;
  }

  SetModeInfo(ChangeMode(m_modeInfo, newMode));
}

void MyPositionController::TurnOff()
{
  StopLocationFollow();
  SetModeInfo(location::MODE_UNKNOWN_POSITION);
  SetIsVisible(false);
}

void MyPositionController::Invalidate()
{
  location::EMyPositionMode currentMode = GetMode();
  if (currentMode > location::MODE_PENDING_POSITION)
  {
    SetModeInfo(ChangeMode(m_modeInfo, location::MODE_UNKNOWN_POSITION));
    SetModeInfo(ChangeMode(m_modeInfo, location::MODE_PENDING_POSITION));
    m_afterPendingMode = currentMode;
    SetIsVisible(true);
  }
  else if (currentMode == location::MODE_UNKNOWN_POSITION)
  {
    m_afterPendingMode = location::MODE_FOLLOW;
    SetIsVisible(false);
  }
}

void MyPositionController::OnLocationUpdate(location::GpsInfo const & info, bool isNavigable)
{
  Assign(info, isNavigable);

  SetIsVisible(true);

  if (GetMode() == location::MODE_PENDING_POSITION)
  {
    SetModeInfo(ChangeMode(m_modeInfo, m_afterPendingMode));
    m_afterPendingMode = location::MODE_FOLLOW;
  }
}

void MyPositionController::OnCompassUpdate(location::CompassInfo const & info)
{
  Assign(info);
}

void MyPositionController::SetModeListener(location::TMyPositionModeChanged const & fn)
{
  m_modeChangeCallback = fn;
  CallModeListener(m_modeInfo);
}

void MyPositionController::Render(ScreenBase const & screen, ref_ptr<dp::GpuProgramManager> mng,
                                  dp::UniformValuesStorage const  & commonUniforms)
{
  location::EMyPositionMode currentMode = GetMode();
  if (m_shape != nullptr && IsVisible() && currentMode > location::MODE_PENDING_POSITION)
  {
    if (m_isDirtyViewport)
    {
      if (currentMode == location::MODE_FOLLOW)
      {
        ChangeModelView(m_position);
      }
      else if (currentMode == location::MODE_ROTATE_AND_FOLLOW)
      {
        m2::RectD const & pixelRect = screen.PixelRect();
        m2::PointD pxZero(pixelRect.Center().x,
                          pixelRect.maxY() - POSITION_Y_OFFSET * VisualParams::Instance().GetVisualScale());
        ChangeModelView(m_position, m_drawDirection, pxZero, screen);
      }
      m_isDirtyViewport = false;
    }

    m_shape->SetPosition(m_position);
    m_shape->SetAzimuth(m_drawDirection);
    m_shape->SetIsValidAzimuth(IsRotationActive());
    m_shape->SetAccuracy(m_errorRadius);
    m_shape->Render(screen, mng, commonUniforms);
  }
}

void MyPositionController::AnimateStateTransition(location::EMyPositionMode oldMode, location::EMyPositionMode newMode)
{
  if (oldMode == location::MODE_PENDING_POSITION && newMode == location::MODE_FOLLOW)
  {
    if (!TestModeBit(m_modeInfo, FixedZoomBit))
    {
      m2::PointD const size(m_errorRadius, m_errorRadius);
      ChangeModelView(m2::RectD(m_position - size, m_position + size));
    }
  }
  else if (oldMode == location::MODE_ROTATE_AND_FOLLOW && newMode == location::MODE_UNKNOWN_POSITION)
  {
    ChangeModelView(0.0);
  }
}

void MyPositionController::Assign(location::GpsInfo const & info, bool isNavigable)
{
  m2::RectD rect = MercatorBounds::MetresToXY(info.m_longitude,
                                              info.m_latitude,
                                              info.m_horizontalAccuracy);
  m_position = rect.Center();
  m_errorRadius = rect.SizeX() / 2;

  bool const hasBearing = info.HasBearing();
  if ((isNavigable && hasBearing) ||
      (!isNavigable && hasBearing && info.HasSpeed() && info.m_speed > MIN_SPEED_THRESHOLD_MPS))
  {
    SetDirection(my::DegToRad(info.m_bearing));
    m_lastGPSBearing.Reset();
  }

  m_isDirtyViewport = true;
}

void MyPositionController::Assign(location::CompassInfo const & info)
{
  if ((IsInRouting() && GetMode() >= location::MODE_FOLLOW) ||
      (m_lastGPSBearing.ElapsedSeconds() < GPS_BEARING_LIFETIME_S))
  {
    return;
  }

  SetDirection(info.m_bearing);
  m_isDirtyViewport = true;
}

void MyPositionController::SetDirection(double bearing)
{
  m_drawDirection = bearing;
  SetModeInfo(SetModeBit(m_modeInfo, KnownDirectionBit));
}

void MyPositionController::SetModeInfo(uint16_t modeInfo, bool force)
{
  location::EMyPositionMode const newMode = ResetAllModeBits(modeInfo);
  location::EMyPositionMode const oldMode = GetMode();
  m_modeInfo = modeInfo;
  if (newMode != oldMode || force)
  {
    AnimateStateTransition(oldMode, newMode);
    CallModeListener(newMode);
  }
}

location::EMyPositionMode MyPositionController::GetMode() const
{
  return ResetAllModeBits(m_modeInfo);
}

void MyPositionController::CallModeListener(uint16_t mode)
{
  if (m_modeChangeCallback != nullptr)
    m_modeChangeCallback(ResetAllModeBits(mode));
}

bool MyPositionController::IsInRouting() const
{
  return TestModeBit(m_modeInfo, RoutingSessionBit);
}

bool MyPositionController::IsRotationActive() const
{
  return TestModeBit(m_modeInfo, KnownDirectionBit);
}

void MyPositionController::StopLocationFollow()
{
  location::EMyPositionMode currentMode = GetMode();
  if (currentMode > location::MODE_NOT_FOLLOW)
    SetModeInfo(ChangeMode(m_modeInfo, location::MODE_NOT_FOLLOW));
  else if (currentMode == location::MODE_PENDING_POSITION)
    m_afterPendingMode = location::MODE_NOT_FOLLOW;
}

void MyPositionController::StopCompassFollow()
{
  if (GetMode() != location::MODE_ROTATE_AND_FOLLOW)
    return;

  SetModeInfo(ChangeMode(m_modeInfo, location::MODE_FOLLOW));
}

void MyPositionController::ChangeModelView(m2::PointD const & center)
{
  if (m_listener)
    m_listener->ChangeModelView(center);
}

void MyPositionController::ChangeModelView(double azimuth)
{
  if (m_listener)
    m_listener->ChangeModelView(azimuth);
}

void MyPositionController::ChangeModelView(m2::RectD const & rect)
{
  if (m_listener)
    m_listener->ChangeModelView(rect);
}

void MyPositionController::ChangeModelView(m2::PointD const & userPos, double azimuth,
                                           m2::PointD const & pxZero, ScreenBase const & screen)
{
  if (m_listener)
    m_listener->ChangeModelView(userPos, azimuth, pxZero, screen);
}

}