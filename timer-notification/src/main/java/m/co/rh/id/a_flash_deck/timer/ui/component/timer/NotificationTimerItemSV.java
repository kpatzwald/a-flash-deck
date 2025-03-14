/*
 *     Copyright (C) 2021 Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package m.co.rh.id.a_flash_deck.timer.ui.component.timer;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_flash_deck.base.constants.Routes;
import m.co.rh.id.a_flash_deck.base.entity.NotificationTimer;
import m.co.rh.id.a_flash_deck.base.provider.IStatefulViewProvider;
import m.co.rh.id.a_flash_deck.base.provider.navigator.CommonNavConfig;
import m.co.rh.id.a_flash_deck.base.rx.RxDisposer;
import m.co.rh.id.a_flash_deck.base.rx.SerialBehaviorSubject;
import m.co.rh.id.a_flash_deck.timer.R;
import m.co.rh.id.a_flash_deck.timer.provider.command.DeleteNotificationTimerCmd;
import m.co.rh.id.a_flash_deck.timer.provider.command.NotificationTimerQueryCmd;
import m.co.rh.id.a_flash_deck.timer.ui.page.NotificationTimerDetailSVDialog;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class NotificationTimerItemSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = NotificationTimerItemSV.class.getName();
    @NavInject
    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ILogger mLogger;
    private transient CommonNavConfig mCommonNavConfig;
    private transient RxDisposer mRxDisposer;
    private transient NotificationTimerQueryCmd mNotificationTimerQueryCmd;
    private SerialBehaviorSubject<NotificationTimer> mNotificationTimerSubject;

    public NotificationTimerItemSV() {
        mNotificationTimerSubject = new SerialBehaviorSubject<>();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(IStatefulViewProvider.class);
        mLogger = mSvProvider.get(ILogger.class);
        mCommonNavConfig = mSvProvider.get(CommonNavConfig.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNotificationTimerQueryCmd = mSvProvider.get(NotificationTimerQueryCmd.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        ViewGroup rootLayout = (ViewGroup) activity.getLayoutInflater().inflate(
                R.layout.item_notification_timer, container, false);
        Button buttonEdit = rootLayout.findViewById(R.id.button_edit);
        buttonEdit.setOnClickListener(this);
        Button buttonDelete = rootLayout.findViewById(R.id.button_delete);
        buttonDelete.setOnClickListener(this);
        TextView textName = rootLayout.findViewById(R.id.text_name);
        TextView textPeriodMin = rootLayout.findViewById(R.id.text_period_min);
        TextView textSelectedDecks = rootLayout.findViewById(R.id.text_selected_decks);
        mRxDisposer
                .add("clickView_onTimerNotificationChanged",
                        mNotificationTimerSubject
                                .getSubject()
                                .subscribe(timerNotification -> {
                                    Context svContext = mSvProvider.getContext();
                                    textPeriodMin.setText(svContext.getString(R.string.notification_period_every_x_minutes, timerNotification.periodInMinutes));
                                    textName.setText(timerNotification.name);
                                    mRxDisposer.add("clickView_onTimerNotificationChanged_getSelectedDecks"
                                            , mNotificationTimerQueryCmd.getSelectedDecks(timerNotification)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe((decks, throwable) -> {
                                                        if (throwable != null) {
                                                            mLogger
                                                                    .e(TAG, svContext.getString(R.string.error_loading_decks)
                                                                            , throwable);
                                                        } else {
                                                            StringBuilder stringBuilder = new StringBuilder();
                                                            stringBuilder.append("[");
                                                            int size = decks.size();
                                                            for (int i = 0; i < size; i++) {
                                                                stringBuilder.append(decks.get(i).name);
                                                                if (i < size - 1) {
                                                                    stringBuilder.append(", ");
                                                                }
                                                            }
                                                            stringBuilder.append("]");
                                                            textSelectedDecks.setText(stringBuilder.toString());
                                                        }
                                                    }));
                                }));
        return rootLayout;
    }

    public void setNotificationTimer(NotificationTimer notificationTimer) {
        if (notificationTimer != null) {
            mNotificationTimerSubject.onNext(notificationTimer);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        NotificationTimer notificationTimer = mNotificationTimerSubject.getValue();
        if (id == R.id.button_delete) {
            if (notificationTimer != null) {
                Context context = mSvProvider.getContext();
                String title = context.getString(R.string.title_confirm);
                String content = context.getString(R.string.confirm_delete_notification_timer, notificationTimer.name);
                mNavigator.push(Routes.COMMON_BOOLEAN_DIALOG,
                        mCommonNavConfig.args_commonBooleanDialog(title, content),
                        (navigator, navRoute, activity, currentView) -> {
                            Serializable serializable = navRoute.getRouteResult();
                            if (serializable instanceof Boolean) {
                                if ((Boolean) serializable) {
                                    Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                                    CompositeDisposable compositeDisposable = new CompositeDisposable();
                                    compositeDisposable.add(provider.get(DeleteNotificationTimerCmd.class)
                                            .execute(notificationTimer)
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe((timerNotification, throwable) -> {
                                                Context deleteContext = provider.getContext();
                                                if (throwable != null) {
                                                    provider.get(ILogger.class)
                                                            .e(TAG,
                                                                    deleteContext.getString(
                                                                            R.string.error_deleting_notification_timer),
                                                                    throwable);
                                                } else {
                                                    provider.get(ILogger.class)
                                                            .i(TAG,
                                                                    deleteContext.getString(
                                                                            R.string.success_deleting_notification_timer, timerNotification.name));
                                                }
                                            })
                                    );
                                }
                            }
                        });
            }
        } else if (id == R.id.button_edit) {
            mNavigator.push(Routes.NOTIFICATION_TIMER_DETAIL_DIALOG,
                    NotificationTimerDetailSVDialog.Args.forUpdate(notificationTimer.clone()));
        }
    }

    public NotificationTimer getNotificationTimer() {
        return mNotificationTimerSubject.getValue();
    }
}
