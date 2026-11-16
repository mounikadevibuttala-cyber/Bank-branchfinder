package com.example.bankbanchfinder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bankbanchfinder.data.local.FavouriteBranch
import com.example.bankbanchfinder.databinding.ItemFavouriteBranchBinding

class FavouritesAdapter(
    private val onItemClick: (FavouriteBranch) -> Unit,
    private val onCopyClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<FavouriteBranch, FavouritesAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemFavouriteBranchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavouriteBranch) {
            binding.tvBankName.text = item.bank
            binding.tvBranchName.text = "${item.branch}, ${item.city}"
            binding.tvIfscCode.text = "IFSC: ${item.ifsc}"

            binding.btnCopyIfsc.setOnClickListener {
                onCopyClick(item.ifsc)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item.ifsc)
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFavouriteBranchBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<FavouriteBranch>() {
            override fun areItemsTheSame(oldItem: FavouriteBranch, newItem: FavouriteBranch): Boolean {
                return oldItem.ifsc == newItem.ifsc
            }

            override fun areContentsTheSame(oldItem: FavouriteBranch, newItem: FavouriteBranch): Boolean {
                return oldItem == newItem
            }
        }
    }
}
