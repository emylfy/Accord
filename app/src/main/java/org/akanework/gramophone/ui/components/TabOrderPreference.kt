package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter.Companion.mapSettingToTabList
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter.Companion.mapTabListToSetting

// Settings preference that shows a drag-and-drop dialog for reordering library tabs.
// The tab list uses a null sentinel as a separator: tabs above it are visible,
// tabs below are hidden. The separator itself can be dragged to show/hide tabs,
// but at least one tab must remain visible (separator can't go to position 0).
class TabOrderPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {
    private var _value = ""
    var value
        get() = _value
        set(new) {
            _value = new
            persistString(new)
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        _value = getPersistedString((defaultValue as String?) ?: "")
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.tab_order_dialog
    }

    class TabOrderDialog : PreferenceDialogFragmentCompat() {
        private val adapter by lazy { TabOrderAdapter((preference as TabOrderPreference).value) }

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)
            val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerview)
            recyclerView.adapter = adapter
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
                override fun isLongPressDragEnabled() = true
                override fun isItemViewSwipeEnabled() = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    // Constraint: separator can't go to position 0 — at least one tab must be visible
                    if (viewHolder is TabOrderSeparatorViewHolder &&
                        target.bindingAdapterPosition == 0
                    ) return false
                    // Constraint: the last visible tab can't cross below the separator
                    // (would leave zero visible tabs)
                    if (target.bindingAdapterPosition >= adapter.value.indexOf(null) &&
                        viewHolder.bindingAdapterPosition == 0
                    ) return false
                    adapter.value.add(
                        target.bindingAdapterPosition,
                        adapter.value.removeAt(viewHolder.bindingAdapterPosition)
                    )
                    adapter.notifyItemMoved(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            }).attachToRecyclerView(recyclerView)
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val newValue = mapTabListToSetting(adapter.value)
                (preference as TabOrderPreference).let {
                    if (it.callChangeListener(newValue)) {
                        it.value = newValue
                    }
                }
            }
        }

        companion object {
            fun newInstance(key: String): TabOrderDialog {
                return TabOrderDialog().apply {
                    arguments = Bundle().apply {
                        putString(ARG_KEY, key)
                    }
                }
            }
        }
    }

    open class TabOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    class TabOrderItemViewHolder(itemView: View) : TabOrderViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tabName)
    }
    class TabOrderSeparatorViewHolder(itemView: View) : TabOrderViewHolder(itemView)

    class TabOrderAdapter(initialValue: String) : RecyclerView.Adapter<TabOrderViewHolder>() {
        val value = mapSettingToTabList(initialValue).toMutableList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            if (viewType == 2)
                TabOrderSeparatorViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_separator, parent, false)
                )
            else
                TabOrderItemViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_item, parent, false)
                )

        override fun onBindViewHolder(holder: TabOrderViewHolder, position: Int) {
            if (holder is TabOrderItemViewHolder) {
                val item = value[position]!!
                holder.name.text = holder.itemView.context.getString(item.label)
            }
        }

        override fun getItemCount() = value.count()

        override fun getItemViewType(position: Int) = if (value[position] == null) 2 else 1
    }
}
