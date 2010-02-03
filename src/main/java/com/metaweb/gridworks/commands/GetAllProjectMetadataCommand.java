package com.metaweb.gridworks.commands;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONWriter;

import com.metaweb.gridworks.ProjectManager;
import com.metaweb.gridworks.ProjectMetadata;

public class GetAllProjectMetadataCommand extends Command {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		try {
			response.setCharacterEncoding("UTF-8");
	    	response.setHeader("Content-Type", "application/json");
	    	
			JSONWriter writer = new JSONWriter(response.getWriter());
			Properties options = new Properties();
			
			writer.object();
			
			writer.key("projects"); writer.object();
			
			Map<Long, ProjectMetadata> m = ProjectManager.singleton.getAllProjectMetadata();
			for (Long id : m.keySet()) {
				writer.key(id.toString());
				m.get(id).write(writer, options);
			}
			writer.endObject();
			writer.endObject();
		} catch (JSONException e) {
			respondException(response, e);
		}
	}
}