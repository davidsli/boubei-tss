package com.boubei.tss.x;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.boubei.tss.PX;
import com.boubei.tss.dm.DataExport;
import com.boubei.tss.dm.record.Record;
import com.boubei.tss.dm.record.RecordService;
import com.boubei.tss.dm.record.permission.RecordPermission;
import com.boubei.tss.dm.report.Report;
import com.boubei.tss.dm.report.ReportService;
import com.boubei.tss.dm.report.permission.ReportPermission;
import com.boubei.tss.framework.Global;
import com.boubei.tss.framework.persistence.ICommonService;
import com.boubei.tss.framework.web.servlet.AfterUpload;
import com.boubei.tss.modules.cloud.ModuleDef;
import com.boubei.tss.modules.cloud.ModuleService;
import com.boubei.tss.modules.param.Param;
import com.boubei.tss.modules.param.ParamManager;
import com.boubei.tss.um.entity.Role;
import com.boubei.tss.util.EasyUtils;
import com.boubei.tss.util.FileHelper;

/**
 * 模块功能扩展：导入、导出（角色、录入表、报表、授权信息）
 */
@Controller
@RequestMapping("/auth/module/x")
public class ModuleX implements AfterUpload {
	
	@Autowired ModuleService service;
	@Autowired ICommonService commonService;
	@Autowired ReportService reportService;
	@Autowired RecordService recordService;
	
	// 导出模块
	@RequestMapping("/{moduleId}")
	public void export(HttpServletResponse response, 
            @PathVariable("moduleId") Long moduleId) {
		
		ModuleDef def = (ModuleDef) commonService.getEntity(ModuleDef.class, moduleId);
		
		// 模块所包含的角色 及 资源权限
		List<Role> roleList = new ArrayList<Role>();
		List<Object> recordPermissions = new ArrayList<Object>();
		List<Object> reportPermissions = new ArrayList<Object>();
		
		String[] roles = def.getRoles().split(",");
		for(String role : roles) {
			Long roleId = EasyUtils.obj2Long(role);
			Role r = (Role) commonService.getEntity(Role.class, roleId);
			roleList.add( r );
			recordPermissions.addAll(commonService.getList(" from RecordPermission where roleId = ?", roleId));
			reportPermissions.addAll(commonService.getList(" from ReportPermission where roleId = ?", roleId));
		}
		
		// 资源（录入表、报表）
		Set<String> dataSources = new HashSet<String>();
		List<Record> records = new ArrayList<Record>();
		for(Object obj : recordPermissions) {
			RecordPermission p = (RecordPermission) obj;
			Record record = recordService.getRecord(p.getResourceId());
			records.add( record );
			dataSources.add( record.getDatasource() );
		}
		
		List<Report> reports = new ArrayList<Report>();
		for(Object obj : reportPermissions) {
			ReportPermission p = (ReportPermission) obj;
			Report report = reportService.getReport(p.getResourceId());
			reports.add( report );
			dataSources.add( report.getDatasource() );
		}
		
		// 数据源
		List<Param> dsList = ParamManager.getComboParam(PX.DATASOURCE_LIST);
		for(Iterator<Param> it = dsList.iterator(); it.hasNext(); ) {
			if(!dataSources.contains( it.next().getCode() )) {
				it.remove();
			}
		}
 
		// 先输出内容到服务端的导出文件中
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("module", def);
        result.put("roles", roleList);
        result.put("recordPermissions", recordPermissions);
		result.put("reportPermissions", reportPermissions);
        result.put("records", records);
        result.put("reports", reports);
        result.put("datasource", dsList);
        
        String exportPath = DataExport.getExportPath() + "/" + def.getModule() + ".json";
        String json = EasyUtils.obj2Json(result);
        FileHelper.writeFile(exportPath, json, false);
        DataExport.downloadFileByHttp(response, exportPath);
	}
	
	// 导入模块
	public String processUploadFile(HttpServletRequest request,
			String filepath, String orignFileName) throws Exception {
		
		XService service = (XService) Global.getBean("XService");
		service.importModule(filepath);
		
		return "parent.alert('导入成功，如果模块包含有自定义的HTML页，其涉及数据服务需另外修改'); parent.loadInitData();";
	}
}