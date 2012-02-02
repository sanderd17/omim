#pragma once

#include "../search/result.hpp"
#include "../search/params.hpp"

#include "../std/vector.hpp"

#include <QtGui/QWidget>
#include <QtGui/QPixmap>

class QTableWidget;
class QLineEdit;
class QPushButton;
class QTimer;

namespace qt
{

class DrawWidget;

class SearchPanel : public QWidget
{
  DrawWidget * m_pDrawWidget;
  QTableWidget * m_pTable;
  QLineEdit * m_pEditor;
  QPushButton * m_pClearButton;
  QTimer * m_pAnimationTimer;

  QPixmap m_busyIcon;

  /// Stores current search results
  typedef search::Results ResultsT;
  typedef search::Result ResultT;
  vector<ResultT> m_results;
  int volatile m_queryId;

  search::SearchParams m_params;

  Q_OBJECT

signals:
  void SearchResultSignal(ResultsT * result, int queryId);

private:
  void SearchResultThreadFunc(ResultsT const & result, int queryId);
  virtual void showEvent(QShowEvent *);
  virtual void hideEvent(QHideEvent *);

public:
  explicit SearchPanel(DrawWidget * drawWidget, QWidget * parent);
  ~SearchPanel();

private slots:
  void OnSearchPanelItemClicked(int row, int column);
  void OnSearchTextChanged(QString const &);
  /// Called via signal to support multithreading
  void OnSearchResult(ResultsT * result, int queryId);
  void OnViewportChanged();
  void OnAnimationTimer();
  void OnClearButton();
};

}
